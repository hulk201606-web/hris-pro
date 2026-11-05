package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.attendance.entity.AttendanceException;
import com.hris.attendance.entity.DailyAttendance;
import com.hris.attendance.entity.Shift;
import com.hris.attendance.mapper.AttendanceExceptionMapper;
import com.hris.attendance.util.TimeAmountUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 日考勤状态判定与异常生成规则（ATT-F-008 / ATT-F-009）。
 *
 * <p>从 {@link DailyAttendanceService} 拆出，使「计算编排」与「规则判定」各自单一职责；
 * 状态口径集中在此，报表统计（{@link ReportService}）与月结复用同一套常量。</p>
 *
 * <p>优先级：法定节假日 > 假期类型 > 排班班次 > 默认政策（docs/03-03 §4）。
 * 法定节假日由节假日历（att_holiday）经 {@link HolidayService#isWorkday} 判定，
 * 在 {@link DailyAttendanceService#compute} 编排层短路；宽限分级走常量兜底。</p>
 */
@Service
public class AttendanceCalculator {

    /** 迟到宽限分钟：超过上班时刻该值才判迟到 */
    public static final int LATE_GRACE_MINUTES = 15;
    /** 迟到重度阈值分钟：超过该值按旷工半天口径描述并升级预警 */
    public static final int LATE_SEVERE_MINUTES = 60;

    /**
     * 跨天班次判定（B-5）。显式 cross_day=1 优先；历史数据（无标志列值）由
     * end_time&lt;=start_time 派生兜底。判定结果供早退比较与班次响应共用。
     */
    public static boolean isCrossDay(Shift shift) {
        if (shift == null || shift.getStartTime() == null || shift.getEndTime() == null) {
            return false;
        }
        if (shift.getCrossDay() != null && shift.getCrossDay() == 1) {
            return true;
        }
        return !shift.getEndTime().isAfter(shift.getStartTime());
    }

    /** 出勤状态：正常 */
    public static final String STATUS_NORMAL = "NORMAL";
    /** 出勤状态：迟到 */
    public static final String STATUS_LATE = "LATE";
    /** 出勤状态：早退 */
    public static final String STATUS_EARLY = "EARLY";
    /** 出勤状态：旷工 */
    public static final String STATUS_ABSENT = "ABSENT";
    /** 出勤状态：请假 */
    public static final String STATUS_LEAVE = "LEAVE";
    /**
     * 出勤状态：法定节假日（假期/休）。
     *
     * <p>现有枚举（NORMAL/LATE/EARLY/ABSENT/LEAVE）均为「人」的出勤口径，无一是
     * 「日历放假日」的语义，故按实体注释扩展新增本值：法定节假日无论有无打卡，
     * 一律不判旷工/迟到/早退，也不生成异常单。</p>
     */
    public static final String STATUS_HOLIDAY = "HOLIDAY";

    /** 异常类型：迟到 */
    public static final String EXC_LATE = "LATE";
    /** 异常类型：早退 */
    public static final String EXC_EARLY = "EARLY";
    /** 异常类型：缺卡 */
    public static final String EXC_MISSING_PUNCH = "MISSING_PUNCH";
    /** 异常类型：旷工 */
    public static final String EXC_ABSENT = "ABSENT";

    private final AttendanceExceptionMapper exceptionMapper;

    public AttendanceCalculator(AttendanceExceptionMapper exceptionMapper) {
        this.exceptionMapper = exceptionMapper;
    }

    /**
     * 状态判定：请假优先（当日请假时长达到 8h 视为全天假），
     * 其次未打卡为旷工，再按上下班时刻比对班次（含宽限）判迟到/早退。
     */
    public String resolveStatus(Shift shift, LocalDateTime punchIn, LocalDateTime punchOut,
                                BigDecimal leaveHours) {
        if (TimeAmountUtil.amount(leaveHours)
                .compareTo(BigDecimal.valueOf(TimeAmountUtil.HOURS_PER_DAY)) >= 0) {
            return STATUS_LEAVE;
        }
        if (shift == null) {
            return punchIn == null && punchOut == null ? STATUS_ABSENT : STATUS_NORMAL;
        }
        if (punchIn == null && punchOut == null) {
            return STATUS_ABSENT;
        }
        LocalTime lateLine = shift.getStartTime().plusMinutes(LATE_GRACE_MINUTES);
        if (punchIn != null && punchIn.toLocalTime().isAfter(lateLine)) {
            return STATUS_LATE;
        }
        if (punchOut != null && isEarlyLeave(shift, punchOut)) {
            return STATUS_EARLY;
        }
        return STATUS_NORMAL;
    }

    /**
     * 早退判定（B-5）：普通班次按 LocalTime 直接比较；跨天班次的下班时间按
     * 「次日」语义比较——打卡时刻落在当晚（&gt;= 上班时间）即尚未到次日语义，必为早退；
     * 凌晨时刻（&lt; 上班时间）与下班时间直接比较。
     */
    private boolean isEarlyLeave(Shift shift, LocalDateTime punchOut) {
        LocalTime out = punchOut.toLocalTime();
        if (!isCrossDay(shift)) {
            return out.isBefore(shift.getEndTime());
        }
        return out.compareTo(shift.getStartTime()) >= 0 || out.isBefore(shift.getEndTime());
    }

    /** 依据日考勤结果与班次生成异常单，返回本次新增条数。 */
    public int raiseExceptions(DailyAttendance daily, Shift shift) {
        List<String> types = exceptionTypes(daily);
        int created = 0;
        for (String type : types) {
            if (raiseOne(daily, type, shift)) {
                created++;
            }
        }
        return created;
    }

    /** 由日考勤状态推导应生成的异常类型；无异常返回空列表。 */
    private List<String> exceptionTypes(DailyAttendance daily) {
        List<String> types = new ArrayList<>();
        String status = daily.getAttStatus() == null ? STATUS_NORMAL : daily.getAttStatus();
        switch (status) {
            case STATUS_LATE -> types.add(EXC_LATE);
            case STATUS_EARLY -> types.add(EXC_EARLY);
            case STATUS_ABSENT -> types.add(EXC_ABSENT);
            // 法定节假日不生成任何异常单（即使无打卡，也不产生缺卡/旷工）
            case STATUS_HOLIDAY -> {
            }
            default -> {
                if (daily.getPunchIn() == null || daily.getPunchOut() == null) {
                    types.add(EXC_MISSING_PUNCH);
                }
            }
        }
        return types;
    }

    /** 单条异常单去重写入，返回是否新增。 */
    private boolean raiseOne(DailyAttendance daily, String exceptionType, Shift shift) {
        Long cnt = exceptionMapper.selectCount(new LambdaQueryWrapper<AttendanceException>()
                .eq(AttendanceException::getEmpId, daily.getEmpId())
                .eq(AttendanceException::getWorkDate, daily.getWorkDate())
                .eq(AttendanceException::getExceptionType, exceptionType)
                .in(AttendanceException::getStatus,
                        List.of(ExceptionService.STATUS_OPEN, ExceptionService.STATUS_RESOLVED,
                                ExceptionService.STATUS_IGNORED)));
        if (cnt != null && cnt > 0) {
            return false;
        }
        AttendanceException e = new AttendanceException();
        e.setId(IdWorker.getId());
        e.setEmpId(daily.getEmpId());
        e.setWorkDate(daily.getWorkDate());
        e.setExceptionType(exceptionType);
        e.setStatus(ExceptionService.STATUS_OPEN);
        e.setDescription(describe(exceptionType, daily, shift));
        exceptionMapper.insert(e);
        return true;
    }

    /** 异常说明文案；迟到按超出宽限的程度分级（轻度 / 重度）。 */
    private String describe(String exceptionType, DailyAttendance daily, Shift shift) {
        return switch (exceptionType) {
            case EXC_LATE -> "上班打卡 " + (daily.getPunchIn() == null ? "缺失" : daily.getPunchIn())
                    + severity(daily, shift);
            case EXC_EARLY -> "下班打卡 " + (daily.getPunchOut() == null ? "缺失" : daily.getPunchOut())
                    + "，早于班次下班时间";
            case EXC_MISSING_PUNCH -> "当日上下班打卡不完整，需补卡或说明";
            default -> "当日无任何打卡记录，按旷工处理";
        };
    }

    /** 迟到分级：相对班次上班时刻超出重度阈值时，按旷工半天口径预警。 */
    private String severity(DailyAttendance daily, Shift shift) {
        if (shift == null || daily.getPunchIn() == null) {
            return "，轻度迟到（超出宽限 " + LATE_GRACE_MINUTES + " 分钟）";
        }
        long lateMinutes = Duration.between(shift.getStartTime(), daily.getPunchIn().toLocalTime())
                .toMinutes();
        if (lateMinutes > LATE_SEVERE_MINUTES) {
            return "，重度迟到（超出 " + LATE_SEVERE_MINUTES + " 分钟），按旷工半天预警";
        }
        return "，轻度迟到（超出宽限 " + LATE_GRACE_MINUTES + " 分钟）";
    }
}

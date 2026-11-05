package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.attendance.dto.DailyDtos.DailyAttendanceResponse;
import com.hris.attendance.dto.DailyDtos.DailyComputeRequest;
import com.hris.attendance.dto.DailyDtos.DailyComputeResult;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.entity.DailyAttendance;
import com.hris.attendance.entity.MonthlyClosing;
import com.hris.attendance.entity.PayrollCycle;
import com.hris.attendance.entity.PunchRecord;
import com.hris.attendance.entity.Shift;
import com.hris.attendance.entity.ShiftAssignment;
import com.hris.attendance.mapper.DailyAttendanceMapper;
import com.hris.attendance.mapper.MonthlyClosingMapper;
import com.hris.attendance.mapper.PayrollCycleMapper;
import com.hris.attendance.mapper.ShiftAssignmentMapper;
import com.hris.attendance.util.TimeAmountUtil;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 日考勤计算编排（ATT-F-008）。规则判定委托 {@link AttendanceCalculator}。
 *
 * <p>重算为幂等 upsert（uk(tenant_id, emp_id, work_date)）；异常单按
 * 「同人同日同类型已存在则跳过」去重，避免重复生成待办。</p>
 */
@Service
public class DailyAttendanceService {

    private final DailyAttendanceMapper dailyMapper;
    private final ShiftAssignmentMapper assignmentMapper;
    private final PunchService punchService;
    private final ShiftService shiftService;
    private final LeaveQueryService leaveQueryService;
    private final OvertimeService overtimeService;
    private final AttendanceCalculator calculator;
    private final HolidayService holidayService;
    private final PayrollCycleMapper cycleMapper;
    private final MonthlyClosingMapper closingMapper;

    public DailyAttendanceService(DailyAttendanceMapper dailyMapper,
                                  ShiftAssignmentMapper assignmentMapper,
                                  PunchService punchService,
                                  ShiftService shiftService,
                                  LeaveQueryService leaveQueryService,
                                  OvertimeService overtimeService,
                                  AttendanceCalculator calculator,
                                  HolidayService holidayService,
                                  PayrollCycleMapper cycleMapper,
                                  MonthlyClosingMapper closingMapper) {
        this.dailyMapper = dailyMapper;
        this.assignmentMapper = assignmentMapper;
        this.punchService = punchService;
        this.shiftService = shiftService;
        this.leaveQueryService = leaveQueryService;
        this.overtimeService = overtimeService;
        this.calculator = calculator;
        this.holidayService = holidayService;
        this.cycleMapper = cycleMapper;
        this.closingMapper = closingMapper;
    }

    /** 触发日考勤计算。empIds 为空则对该日有排班的全部员工重算。 */
    @Transactional(rollbackFor = Exception.class)
    public DailyComputeResult compute(DailyComputeRequest req) {
        LocalDate workDate = req.workDate();
        ensurePayrollNotClosed(workDate);
        List<Long> empIds = req.empIds() == null || req.empIds().isEmpty()
                ? resolveAssignedEmpIds(workDate)
                : req.empIds();

        int normal = 0;
        int late = 0;
        int early = 0;
        int absent = 0;
        int leave = 0;
        int newExceptions = 0;
        int computed = 0;

        for (Long empId : empIds) {
            Computed result = computeOne(empId, workDate);
            if (result == null) {
                continue;
            }
            computed++;
            String status = result.daily().getAttStatus() == null
                    ? AttendanceCalculator.STATUS_NORMAL
                    : result.daily().getAttStatus();
            switch (status) {
                case AttendanceCalculator.STATUS_LATE -> late++;
                case AttendanceCalculator.STATUS_EARLY -> early++;
                case AttendanceCalculator.STATUS_ABSENT -> absent++;
                case AttendanceCalculator.STATUS_LEAVE -> leave++;
                // 法定节假日不属于任何出勤口径，不计入状态分布（仍计入 employeeCount）
                case AttendanceCalculator.STATUS_HOLIDAY -> {
                }
                default -> normal++;
            }
            // 法定节假日不生成任何异常单（无打卡也不产生缺卡/旷工），编排层直接跳过
            if (!AttendanceCalculator.STATUS_HOLIDAY.equals(status)) {
                newExceptions += calculator.raiseExceptions(result.daily(), result.shift());
            }
        }
        return new DailyComputeResult(workDate, computed, normal, late, early, absent, leave,
                newExceptions);
    }

    /** 单员工单日计算中间结果：日考勤 + 命中的班次（供异常判定复用，避免重复查询）。 */
    private record Computed(DailyAttendance daily, Shift shift) {
    }

    /** 日考勤分页查询（按员工 / 日期区间 / 状态）。 */
    public PageResult<DailyAttendanceResponse> list(Long empId, LocalDate from, LocalDate to,
                                                     String attStatus, long page, long size) {
        Page<DailyAttendance> p = new Page<>(page, size);
        LambdaQueryWrapper<DailyAttendance> qw = new LambdaQueryWrapper<>();
        if (empId != null) {
            qw.eq(DailyAttendance::getEmpId, empId);
        }
        if (from != null) {
            qw.ge(DailyAttendance::getWorkDate, from);
        }
        if (to != null) {
            qw.le(DailyAttendance::getWorkDate, to);
        }
        if (attStatus != null && !attStatus.isBlank()) {
            qw.eq(DailyAttendance::getAttStatus, attStatus.trim().toUpperCase(Locale.ROOT));
        }
        qw.orderByDesc(DailyAttendance::getWorkDate).orderByAsc(DailyAttendance::getId);

        Page<DailyAttendance> result = dailyMapper.selectPage(p, qw);
        List<DailyAttendanceResponse> records = result.getRecords().stream()
                .map(this::toResponse).toList();
        return new PageResult<>(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 单条日考勤明细。 */
    public DailyAttendanceResponse get(Long empId, LocalDate workDate) {
        DailyAttendance daily = find(empId, workDate);
        if (daily == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该员工当日暂无考勤结果");
        }
        return toResponse(daily);
    }

    /** 查询某周期内的日考勤（供月结与薪酬取数）。 */
    public List<DailyAttendance> findByRange(LocalDate from, LocalDate to) {
        return dailyMapper.selectList(new LambdaQueryWrapper<DailyAttendance>()
                .ge(DailyAttendance::getWorkDate, from)
                .le(DailyAttendance::getWorkDate, to)
                .orderByAsc(DailyAttendance::getWorkDate));
    }

    /** 单员工单日计算，返回落库后的日考勤结果；休息日（无排班且无打卡）返回 null。 */
    private Computed computeOne(Long empId, LocalDate workDate) {
        List<PunchRecord> punches = punchService.findByDate(empId, workDate);
        boolean legalHoliday = isLegalHoliday(workDate);
        Shift shift = resolveShift(empId, workDate);
        if (!legalHoliday && shift == null && punches.isEmpty()) {
            return null;
        }
        LocalDateTime punchIn = punches.stream()
                .filter(p -> PunchService.TYPE_IN.equals(p.getType()))
                .map(PunchRecord::getPunchTime)
                .min(Comparator.naturalOrder())
                .orElse(null);
        LocalDateTime punchOut = punches.stream()
                .filter(p -> PunchService.TYPE_OUT.equals(p.getType()))
                .map(PunchRecord::getPunchTime)
                .max(Comparator.naturalOrder())
                .orElse(null);

        BigDecimal leaveHours = leaveQueryService.leaveHoursOn(empId, workDate);
        BigDecimal overtimeHours = overtimeService.overtimeHoursOn(empId, workDate);
        // 规格优先级：法定节假日 > 假期类型 > 排班班次 > 默认政策。
        // 节假日无论有无打卡一律置为假期态，跳过迟到/早退/旷工判定；
        // 有打卡的加班记法仍由既有 overtimeHoursOn 链路决定，不在此破坏。
        String status = legalHoliday ? AttendanceCalculator.STATUS_HOLIDAY
                : calculator.resolveStatus(shift, punchIn, punchOut, leaveHours);
        return new Computed(upsert(empId, workDate, punchIn, punchOut, leaveHours, overtimeHours,
                status), shift);
    }

    /** 法定节假日判定：按租户查 att_holiday，日历配置优先于星期推断。 */
    private boolean isLegalHoliday(LocalDate workDate) {
        return HolidayService.TYPE_HOLIDAY.equals(holidayService.isWorkday(workDate).source());
    }

    /**
     * 月结守卫（§8.5-5）：覆盖该日期的薪酬取数周期若已完成月结（DONE），
     * 历史结果不可重算，差异一律走调整通道。
     */
    private void ensurePayrollNotClosed(LocalDate workDate) {
        List<PayrollCycle> cycles = cycleMapper.selectList(new LambdaQueryWrapper<PayrollCycle>()
                .le(PayrollCycle::getStartDate, workDate)
                .ge(PayrollCycle::getEndDate, workDate));
        for (PayrollCycle cycle : cycles) {
            MonthlyClosing closing = closingMapper.selectOne(
                    new LambdaQueryWrapper<MonthlyClosing>()
                            .eq(MonthlyClosing::getPeriodCode, cycle.getPeriodCode())
                            .last("LIMIT 1"));
            if (closing != null && MonthlyClosingService.STATUS_DONE.equals(closing.getStatus())) {
                throw new BizException(ErrorCode.ATT_ERROR, "月结已锁定，请走调整通道");
            }
        }
    }

    /** 落库：存在则更新，不存在则插入。 */
    private DailyAttendance upsert(Long empId, LocalDate workDate, LocalDateTime punchIn,
                                   LocalDateTime punchOut, BigDecimal leaveHours,
                                   BigDecimal overtimeHours, String status) {
        DailyAttendance daily = find(empId, workDate);
        boolean isNew = daily == null;
        if (isNew) {
            daily = new DailyAttendance();
            daily.setId(IdWorker.getId());
            daily.setEmpId(empId);
            daily.setWorkDate(workDate);
            // employment_id 为 NOT NULL；MVP 无员工域雇佣关系查询接口，落 0 占位（见 OPEN-DECISIONS）
            daily.setEmploymentId(0L);
        }
        daily.setAttStatus(status);
        daily.setPunchIn(punchIn);
        daily.setPunchOut(punchOut);
        daily.setLeaveHours(TimeAmountUtil.amount(leaveHours));
        daily.setOvertimeHours(TimeAmountUtil.amount(overtimeHours));
        if (isNew) {
            dailyMapper.insert(daily);
        } else {
            if (dailyMapper.updateById(daily) == 0) {
                throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
            }
        }
        return daily;
    }

    /** 取该日有排班的员工 ID 列表（未指定员工时按排班范围重算）。 */
    private List<Long> resolveAssignedEmpIds(LocalDate workDate) {
        return assignmentMapper.selectList(new LambdaQueryWrapper<ShiftAssignment>()
                        .eq(ShiftAssignment::getWorkDate, workDate))
                .stream().map(ShiftAssignment::getEmpId).distinct().toList();
    }

    private Shift resolveShift(Long empId, LocalDate workDate) {
        ShiftAssignment assignment = assignmentMapper.selectOne(
                new LambdaQueryWrapper<ShiftAssignment>()
                        .eq(ShiftAssignment::getEmpId, empId)
                        .eq(ShiftAssignment::getWorkDate, workDate)
                        .last("LIMIT 1"));
        return assignment == null ? null : shiftService.require(assignment.getShiftId());
    }

    private DailyAttendance find(Long empId, LocalDate workDate) {
        return dailyMapper.selectOne(new LambdaQueryWrapper<DailyAttendance>()
                .eq(DailyAttendance::getEmpId, empId)
                .eq(DailyAttendance::getWorkDate, workDate)
                .last("LIMIT 1"));
    }

    private DailyAttendanceResponse toResponse(DailyAttendance d) {
        return new DailyAttendanceResponse(d.getId(), d.getEmpId(), d.getWorkDate(),
                d.getEmploymentId(), d.getAttStatus(), d.getPunchIn(), d.getPunchOut(),
                TimeAmountUtil.amount(d.getLeaveHours()),
                TimeAmountUtil.amount(d.getOvertimeHours()), d.getUpdatedAt());
    }
}

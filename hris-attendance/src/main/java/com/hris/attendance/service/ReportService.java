package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.attendance.dto.ReportDtos.AttendanceRateItem;
import com.hris.attendance.dto.ReportDtos.LeaveConsumeItem;
import com.hris.attendance.dto.ReportDtos.OvertimeStatItem;
import com.hris.attendance.entity.DailyAttendance;
import com.hris.attendance.entity.LeaveQuota;
import com.hris.attendance.entity.LeaveType;
import com.hris.attendance.entity.OvertimeRequest;
import com.hris.attendance.mapper.LeaveQuotaMapper;
import com.hris.attendance.mapper.LeaveTypeMapper;
import com.hris.attendance.mapper.OvertimeRequestMapper;
import com.hris.attendance.util.TimeAmountUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 考勤报表（ATT-F-025）。出勤率 / 加班时长 / 假期消耗三类统计。
 *
 * <p>注意：att_daily_attendance 无 dept_id 列，员工域也未提供按部门批量取人的内部接口，
 * 因此 MVP 只能按员工维度聚合；部门维度待二期补接口后叠加（见 OPEN-DECISIONS）。</p>
 *
 * <p>统计走内存聚合，单次受日期区间约束（上限同排班 366 天），避免全表扫描。</p>
 */
@Service
public class ReportService {

    /** 出勤率统计桶下标：0=总天数 1=正常 2=迟到 3=早退 4=旷工 5=请假 */
    private static final int IDX_TOTAL = 0;
    private static final int IDX_NORMAL = 1;
    private static final int IDX_LATE = 2;
    private static final int IDX_EARLY = 3;
    private static final int IDX_ABSENT = 4;
    private static final int IDX_LEAVE = 5;

    private final DailyAttendanceService dailyService;
    private final OvertimeRequestMapper overtimeMapper;
    private final LeaveQuotaMapper quotaMapper;
    private final LeaveTypeMapper leaveTypeMapper;

    public ReportService(DailyAttendanceService dailyService, OvertimeRequestMapper overtimeMapper,
                         LeaveQuotaMapper quotaMapper, LeaveTypeMapper leaveTypeMapper) {
        this.dailyService = dailyService;
        this.overtimeMapper = overtimeMapper;
        this.quotaMapper = quotaMapper;
        this.leaveTypeMapper = leaveTypeMapper;
    }

    /** 出勤率统计（按员工）：normalDays / workDays。 */
    public List<AttendanceRateItem> attendanceRate(LocalDate from, LocalDate to) {
        Map<Long, long[]> buckets = new LinkedHashMap<>();
        for (DailyAttendance d : dailyService.findByRange(from, to)) {
            long[] bucket = buckets.computeIfAbsent(d.getEmpId(), k -> new long[6]);
            bucket[IDX_TOTAL]++;
            if (AttendanceCalculator.STATUS_LATE.equals(d.getAttStatus())) {
                bucket[IDX_LATE]++;
            } else if (AttendanceCalculator.STATUS_EARLY.equals(d.getAttStatus())) {
                bucket[IDX_EARLY]++;
            } else if (AttendanceCalculator.STATUS_ABSENT.equals(d.getAttStatus())) {
                bucket[IDX_ABSENT]++;
            } else if (AttendanceCalculator.STATUS_LEAVE.equals(d.getAttStatus())) {
                bucket[IDX_LEAVE]++;
            } else {
                bucket[IDX_NORMAL]++;
            }
        }
        List<AttendanceRateItem> items = new ArrayList<>(buckets.size());
        buckets.forEach((empId, b) -> items.add(new AttendanceRateItem(empId, b[IDX_TOTAL],
                b[IDX_NORMAL], b[IDX_LATE], b[IDX_EARLY], b[IDX_ABSENT], b[IDX_LEAVE],
                ratio(BigDecimal.valueOf(b[IDX_NORMAL]), BigDecimal.valueOf(b[IDX_TOTAL])))));
        return items;
    }

    /** 加班时长统计（按员工，仅统计审批通过单）。 */
    public List<OvertimeStatItem> overtimeStat(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<OvertimeRequest> list = overtimeMapper.selectList(
                new LambdaQueryWrapper<OvertimeRequest>()
                        .eq(OvertimeRequest::getStatus, OvertimeService.STATUS_APPROVED)
                        .ge(OvertimeRequest::getStartTime, start)
                        .lt(OvertimeRequest::getStartTime, end));

        Map<Long, BigDecimal> hours = new LinkedHashMap<>();
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (OvertimeRequest ot : list) {
            hours.merge(ot.getEmpId(), TimeAmountUtil.amount(ot.getHours()), BigDecimal::add);
            counts.merge(ot.getEmpId(), 1L, Long::sum);
        }
        List<OvertimeStatItem> items = new ArrayList<>(hours.size());
        hours.forEach((empId, h) -> items.add(
                new OvertimeStatItem(empId, h, counts.getOrDefault(empId, 0L))));
        return items;
    }

    /** 假期消耗统计（按假种，指定周期）：consumeRate = usedQuota / totalQuota。 */
    public List<LeaveConsumeItem> leaveConsume(String period) {
        List<LeaveQuota> quotas = quotaMapper.selectList(new LambdaQueryWrapper<LeaveQuota>()
                .eq(LeaveQuota::getPeriod, period.trim())
                .orderByAsc(LeaveQuota::getLeaveTypeId));

        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        Map<Long, BigDecimal> used = new LinkedHashMap<>();
        for (LeaveQuota q : quotas) {
            totals.merge(q.getLeaveTypeId(), TimeAmountUtil.amount(q.getTotalQuota()),
                    BigDecimal::add);
            used.merge(q.getLeaveTypeId(), TimeAmountUtil.amount(q.getUsedQuota()),
                    BigDecimal::add);
        }

        Map<Long, LeaveType> types = new LinkedHashMap<>();
        if (!totals.isEmpty()) {
            for (LeaveType t : leaveTypeMapper.selectBatchIds(List.copyOf(totals.keySet()))) {
                types.put(t.getId(), t);
            }
        }

        List<LeaveConsumeItem> items = new ArrayList<>(totals.size());
        totals.forEach((typeId, total) -> {
            BigDecimal u = used.getOrDefault(typeId, BigDecimal.ZERO);
            LeaveType t = types.get(typeId);
            items.add(new LeaveConsumeItem(typeId, t == null ? null : t.getName(), total, u,
                    ratio(u, total)));
        });
        return items;
    }

    /** 比值（4 位小数，分母为 0 返回 0）。 */
    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }
}

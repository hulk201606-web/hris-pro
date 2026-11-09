package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.attendance.dto.DailyDtos.DailyAttendanceBriefItem;
import com.hris.attendance.dto.DailyDtos.DailyAttendanceResponse;
import com.hris.attendance.entity.DailyAttendance;
import com.hris.attendance.entity.MonthlyClosing;
import com.hris.attendance.entity.PayrollCycle;
import com.hris.attendance.mapper.DailyAttendanceMapper;
import com.hris.attendance.mapper.MonthlyClosingMapper;
import com.hris.attendance.mapper.PayrollCycleMapper;
import com.hris.attendance.util.TimeAmountUtil;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DailyAttendanceService 的查询/守卫/视图组装工具（ATT-F-008）。
 *
 * <p>从 DailyAttendanceService 拆分而来（单文件 ≤300 行门禁），mapper 由调用方传入，
 * 因此宿主构造器签名（12 参）保持不变。计算编排仍在宿主 {@code compute} 内。</p>
 */
final class DailyAttendanceSupport {

    private DailyAttendanceSupport() {
    }

    /**
     * 考勤简报聚合（批6#10 跨域契约，供 cmp 算薪消费）：按员工聚合周期内
     * 迟到次数（LATE）与全天缺勤天数（ABSENT）；无任何日考勤记录的员工返回 0 值项。
     */
    static List<DailyAttendanceBriefItem> attendanceBrief(DailyAttendanceMapper dailyMapper, List<Long> empIds,
                                                         LocalDate periodStart, LocalDate periodEnd) {
        List<Long> ids = empIds == null ? List.of()
                : empIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<DailyAttendance> rows = dailyMapper.selectList(new LambdaQueryWrapper<DailyAttendance>()
                .in(DailyAttendance::getEmpId, ids)
                .ge(DailyAttendance::getWorkDate, periodStart)
                .le(DailyAttendance::getWorkDate, periodEnd));
        Map<Long, long[]> agg = new LinkedHashMap<>();
        for (DailyAttendance row : rows) {
            long[] counts = agg.computeIfAbsent(row.getEmpId(), k -> new long[2]);
            if (AttendanceCalculator.STATUS_LATE.equals(row.getAttStatus())) {
                counts[0]++;
            }
            if (AttendanceCalculator.STATUS_ABSENT.equals(row.getAttStatus())) {
                counts[1]++;
            }
        }
        return ids.stream()
                .map(id -> {
                    long[] counts = agg.getOrDefault(id, new long[2]);
                    return new DailyAttendanceBriefItem(id, counts[0], counts[1]);
                })
                .toList();
    }

    /**
     * 月结守卫（§8.5-5）：覆盖该日期的薪酬取数周期若已完成月结（DONE），
     * 历史结果不可重算，差异一律走调整通道。
     */
    static void ensurePayrollNotClosed(PayrollCycleMapper cycleMapper, MonthlyClosingMapper closingMapper,
                                       LocalDate workDate) {
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

    static DailyAttendance find(DailyAttendanceMapper dailyMapper, Long empId, LocalDate workDate) {
        return dailyMapper.selectOne(new LambdaQueryWrapper<DailyAttendance>()
                .eq(DailyAttendance::getEmpId, empId)
                .eq(DailyAttendance::getWorkDate, workDate)
                .last("LIMIT 1"));
    }

    static DailyAttendanceResponse toResponse(DailyAttendance d) {
        return new DailyAttendanceResponse(d.getId(), d.getEmpId(), d.getWorkDate(),
                d.getEmploymentId(), d.getAttStatus(), d.getPunchIn(), d.getPunchOut(),
                TimeAmountUtil.amount(d.getLeaveHours()),
                TimeAmountUtil.amount(d.getOvertimeHours()), d.getUpdatedAt());
    }
}

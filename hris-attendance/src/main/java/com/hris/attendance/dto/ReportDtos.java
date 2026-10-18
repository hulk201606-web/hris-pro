package com.hris.attendance.dto;

import java.math.BigDecimal;

/**
 * 考勤报表 DTO（ATT-F-025 部门出勤率 / 加班时长 / 假期消耗）。
 *
 * <p>注意：DDL 的 att_daily_attendance 无 dept_id 列，且员工域未提供按部门批量取人的内部接口，
 * 因此 MVP 按员工维度聚合；部门维度待二期补员工域接口后叠加（见 OPEN-DECISIONS）。</p>
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** 出勤率行（按员工）。attendanceRate = normalDays / workDays。 */
    public record AttendanceRateItem(Long empId, long workDays, long normalDays, long lateDays,
                                     long earlyDays, long absentDays, long leaveDays,
                                     BigDecimal attendanceRate) {
    }

    /** 加班统计行（按员工）。 */
    public record OvertimeStatItem(Long empId, BigDecimal totalHours, long times) {
    }

    /** 假期消耗行（按假种）。consumeRate = usedQuota / totalQuota。 */
    public record LeaveConsumeItem(Long leaveTypeId, String leaveTypeName, BigDecimal totalQuota,
                                   BigDecimal usedQuota, BigDecimal consumeRate) {
    }
}

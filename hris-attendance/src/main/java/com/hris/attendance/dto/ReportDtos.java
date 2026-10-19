package com.hris.attendance.dto;

import java.math.BigDecimal;

/**
 * 考勤报表相关 DTO（域：考勤，分层：DTO/接口契约）。假期消耗统计等报表数据结构。
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

package com.hris.compensation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤域跨服务契约 DTO（域：薪酬，分层：DTO/Feign 契约）。薪酬域通过 Feign 调用考勤域时的内部数据结构（月结汇总/日明细）。
 */
public final class AttendanceContractDtos {

    private AttendanceContractDtos() {
    }

    /** 日考勤响应（考勤域 DailyAttendanceResponse 本地契约）。 */
    public record DailyAttendanceResponse(Long id, Long empId, LocalDate workDate, Long employmentId,
                                          String attStatus, LocalDateTime punchIn,
                                          LocalDateTime punchOut, BigDecimal leaveHours,
                                          BigDecimal overtimeHours, LocalDateTime updatedAt) {
    }

    /** 考勤简报行（F-010 契约：GET /internal/att/attendance-brief?empIds=1,2&periodStart=&periodEnd=）。 */
    public record AttendanceBriefRow(Long empId, long lateCount, long absentDays) {
    }

    /** 月结结果集响应（考勤域 ClosingDetailResponse 本地契约，供算薪取汇总）。 */
    public record ClosingDetailResponse(String periodCode, String status, LocalDate startDate,
                                        LocalDate endDate, long employeeCount, long normalDays,
                                        long lateDays, long earlyDays, long absentDays,
                                        long leaveDays, BigDecimal overtimeHours,
                                        BigDecimal leaveHours, long pendingExceptionCount,
                                        long adjustmentCount) {
    }
}

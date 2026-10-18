package com.hris.compensation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤域内部契约的本地副本（字段与 hris-attendance 对齐，docs/07）。
 *
 * <p>跨域响应体在补偿域重新声明为本地 record，避免模块间编译期耦合；
 * JSON 反序列化按字段名映射，字段变更需同步两处。</p>
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

    /** 月结结果集响应（考勤域 ClosingDetailResponse 本地契约，供算薪取汇总）。 */
    public record ClosingDetailResponse(String periodCode, String status, LocalDate startDate,
                                        LocalDate endDate, long employeeCount, long normalDays,
                                        long lateDays, long earlyDays, long absentDays,
                                        long leaveDays, BigDecimal overtimeHours,
                                        BigDecimal leaveHours, long pendingExceptionCount,
                                        long adjustmentCount) {
    }
}

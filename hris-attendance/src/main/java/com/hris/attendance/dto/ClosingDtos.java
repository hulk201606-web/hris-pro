package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 月结 DTO（ATT-F-023 考勤月结 / ATT-F-024 月结红冲与调整）。
 */
public final class ClosingDtos {

    private ClosingDtos() {
    }

    /** 月结执行请求。周期需先在 att_payroll_cycle 登记。 */
    public record ClosingRunRequest(
            @NotBlank(message = "周期编码不能为空") String periodCode) {
    }

    /** 月结批次响应。pendingExceptionCount 为锁账时未决异常数（DONE 时恒为 0）。 */
    public record ClosingResponse(Long id, String periodCode, String status, LocalDateTime closedAt,
                                  long pendingExceptionCount, LocalDateTime createdAt,
                                  LocalDateTime updatedAt) {
    }

    /** 月结结果集响应（供薪酬域取数）。 */
    public record ClosingDetailResponse(String periodCode, String status, LocalDate startDate,
                                        LocalDate endDate, long employeeCount, long normalDays,
                                        long lateDays, long earlyDays, long absentDays,
                                        long leaveDays, BigDecimal overtimeHours,
                                        BigDecimal leaveHours, long pendingExceptionCount,
                                        long adjustmentCount) {
    }

    /** 调整项创建请求：锁账后差异不改历史，作为调整项进入下期。 */
    public record AdjustmentCreateRequest(
            @NotNull(message = "员工 ID 不能为空") Long empId,
            @NotNull(message = "月结批次 ID 不能为空") Long closingId,
            @NotBlank(message = "调整类型不能为空") String adjustType,
            @NotNull(message = "调整量不能为空") BigDecimal amount,
            String reason) {
    }

    /** 调整项响应。 */
    public record AdjustmentResponse(Long id, Long empId, Long closingId, String adjustType,
                                     BigDecimal amount, String reason, LocalDateTime createdAt) {
    }
}

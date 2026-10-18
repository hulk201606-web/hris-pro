package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PayrollAdjustment DTO（由 DDL 自动生成）。
 */
public final class PayrollAdjustmentDtos {

    private PayrollAdjustmentDtos() {
    }

    /** 创建请求。 */
    public record PayrollAdjustmentCreateRequest(@NotNull(message = "runId 不能为空") Long runId, @NotNull(message = "empId 不能为空") Long empId, @NotNull(message = "adjustType 不能为空") String adjustType, @NotNull(message = "amount 不能为空") BigDecimal amount, String reason) {
    }

    /** 更新请求。 */
    public record PayrollAdjustmentUpdateRequest(Long runId, Long empId, String adjustType, BigDecimal amount, String reason) {
    }

    /** 响应。 */
    public record PayrollAdjustmentResponse(Long id, Long runId, Long empId, String adjustType, BigDecimal amount, String reason, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

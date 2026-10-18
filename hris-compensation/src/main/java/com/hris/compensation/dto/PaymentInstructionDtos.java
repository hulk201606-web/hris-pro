package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * PaymentInstruction DTO（由 DDL 自动生成）。
 */
public final class PaymentInstructionDtos {

    private PaymentInstructionDtos() {
    }

    /** 创建请求。 */
    public record PaymentInstructionCreateRequest(@NotNull(message = "runId 不能为空") Long runId, @NotNull(message = "empId 不能为空") Long empId, @NotNull(message = "payStatus 不能为空") String payStatus, LocalDateTime paidAt) {
    }

    /** 更新请求。 */
    public record PaymentInstructionUpdateRequest(Long runId, Long empId, String payStatus, LocalDateTime paidAt) {
    }

    /** 响应。 */
    public record PaymentInstructionResponse(Long id, Long runId, Long empId, String payStatus, LocalDateTime paidAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

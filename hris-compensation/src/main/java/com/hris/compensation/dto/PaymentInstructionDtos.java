package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 发放指令相关 DTO（域：薪酬，分层：DTO/接口契约）。银行发放指令的查询/执行结构。
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

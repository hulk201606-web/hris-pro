package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * PayrollDetail DTO（由 DDL 自动生成）。
 */
public final class PayrollDetailDtos {

    private PayrollDetailDtos() {
    }

    /** 创建请求。 */
    public record PayrollDetailCreateRequest(@NotNull(message = "runId 不能为空") Long runId, @NotNull(message = "empId 不能为空") Long empId, String inputSnapshot, String amountCipher, String amountMask, String netAmountCipher, String netAmountMask) {
    }

    /** 更新请求。 */
    public record PayrollDetailUpdateRequest(Long runId, Long empId, String inputSnapshot, String amountCipher, String amountMask, String netAmountCipher, String netAmountMask) {
    }

    /** 响应。 */
    /**
     * 响应。密域字段 amountCipher/netAmountCipher 不在通用响应暴露（薪酬密域下沉，见 SalarySecretDtos）；
     * 仅回掩码，密文经 GET /api/v1/cmp/salary-secret/payroll-details/{id} 专用端点读取。
     */
    public record PayrollDetailResponse(Long id, Long runId, Long empId, String inputSnapshot, String amountMask, String netAmountMask, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

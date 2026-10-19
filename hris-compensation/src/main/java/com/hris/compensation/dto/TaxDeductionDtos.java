package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 个税专项附加扣除相关 DTO（域：薪酬，分层：DTO/接口契约，密域）。专项附加扣除信息的创建/更新/查询结构。
 */
public final class TaxDeductionDtos {

    private TaxDeductionDtos() {
    }

    /** 创建请求。 */
    public record TaxDeductionCreateRequest(@NotNull(message = "empId 不能为空") Long empId, @NotNull(message = "effectiveFrom 不能为空") LocalDate effectiveFrom, LocalDate effectiveTo, String deductionAmountCipher, String deductionAmountMask) {
    }

    /** 更新请求。 */
    public record TaxDeductionUpdateRequest(Long empId, LocalDate effectiveFrom, LocalDate effectiveTo, String deductionAmountCipher, String deductionAmountMask) {
    }

    /** 响应。 */
    /**
     * 响应。密域字段 deductionAmountCipher 不在通用响应暴露（薪酬密域下沉，见 SalarySecretDtos）；
     * 仅回掩码，密文经 GET /api/v1/cmp/salary-secret/tax-deductions/{id} 专用端点读取。
     */
    public record TaxDeductionResponse(Long id, Long empId, LocalDate effectiveFrom, LocalDate effectiveTo, String deductionAmountMask, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

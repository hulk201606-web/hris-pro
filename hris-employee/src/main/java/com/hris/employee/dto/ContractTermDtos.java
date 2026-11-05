package com.hris.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 合同条款明细 DTO（EMP-F-014 深化）。
 */
public final class ContractTermDtos {

    private ContractTermDtos() {
    }

    /** 条款创建/更新请求。term_type 与 content 必填；effective_to 需晚于 effective_from。 */
    public record ContractTermUpsertRequest(
            @NotBlank(message = "termType 不能为空") String termType,
            @NotBlank(message = "content 不能为空")
            @Size(max = 1000, message = "条款内容不超过 1000 字符") String content,
            @NotNull(message = "effectiveFrom 不能为空") LocalDate effectiveFrom,
            LocalDate effectiveTo) {
    }

    /** 条款响应。 */
    public record ContractTermResponse(
            Long id, Long contractId, String termType, String content,
            LocalDate effectiveFrom, LocalDate effectiveTo) {
    }
}

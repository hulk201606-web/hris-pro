package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ForcedRule DTO（由 DDL 自动生成）。
 */
public final class ForcedRuleDtos {

    private ForcedRuleDtos() {
    }

    /** 创建请求。 */
    public record ForcedRuleCreateRequest(@NotNull(message = "planId 不能为空") Long planId, @NotNull(message = "grade 不能为空") String grade, @NotNull(message = "ratio 不能为空") BigDecimal ratio) {
    }

    /** 更新请求。 */
    public record ForcedRuleUpdateRequest(Long planId, String grade, BigDecimal ratio) {
    }

    /** 响应。 */
    public record ForcedRuleResponse(Long id, Long planId, String grade, BigDecimal ratio, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

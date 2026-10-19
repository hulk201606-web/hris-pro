package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 强制分布规则相关 DTO（域：绩效，分层：DTO/接口契约）。评级比例配置的请求与响应结构。
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

package com.hris.rag.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * EvalCase DTO（由 DDL 自动生成）。
 */
public final class EvalCaseDtos {

    private EvalCaseDtos() {
    }

    /** 创建请求。 */
    public record EvalCaseCreateRequest(@NotNull(message = "evalSetId 不能为空") Long evalSetId, @NotNull(message = "question 不能为空") String question, String expectedRefs, String groundTruth) {
    }

    /** 更新请求。 */
    public record EvalCaseUpdateRequest(Long evalSetId, String question, String expectedRefs, String groundTruth) {
    }

    /** 响应。 */
    public record EvalCaseResponse(Long id, Long evalSetId, String question, String expectedRefs, String groundTruth, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

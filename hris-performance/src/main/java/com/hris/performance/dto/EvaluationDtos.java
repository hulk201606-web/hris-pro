package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Evaluation DTO（由 DDL 自动生成）。
 */
public final class EvaluationDtos {

    private EvaluationDtos() {
    }

    /** 创建请求。 */
    public record EvaluationCreateRequest(@NotNull(message = "taskId 不能为空") Long taskId, @NotNull(message = "evaluatorId 不能为空") Long evaluatorId, String scores, String comment) {
    }

    /** 更新请求。 */
    public record EvaluationUpdateRequest(Long taskId, Long evaluatorId, String scores, String comment) {
    }

    /** 响应。 */
    public record EvaluationResponse(Long id, Long taskId, Long evaluatorId, String scores, String comment, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 评估结果相关 DTO（域：绩效，分层：DTO/接口契约）。评分/评语提交与查询的请求与响应结构。
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

package com.hris.recruit.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * InterviewEval DTO（由 DDL 自动生成）。
 */
public final class InterviewEvalDtos {

    private InterviewEvalDtos() {
    }

    /** 创建请求。 */
    public record InterviewEvalCreateRequest(@NotNull(message = "interviewId 不能为空") Long interviewId, @NotNull(message = "evaluatorId 不能为空") Long evaluatorId, Integer score, String comment, Integer pass) {
    }

    /** 更新请求。 */
    public record InterviewEvalUpdateRequest(Long interviewId, Long evaluatorId, Integer score, String comment, Integer pass) {
    }

    /** 响应。 */
    public record InterviewEvalResponse(Long id, Long interviewId, Long evaluatorId, Integer score, String comment, Integer pass, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

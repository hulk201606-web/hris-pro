package com.hris.recruit.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Interview DTO（由 DDL 自动生成）。
 */
public final class InterviewDtos {

    private InterviewDtos() {
    }

    /** 创建请求。 */
    public record InterviewCreateRequest(@NotNull(message = "applicationId 不能为空") Long applicationId, @NotNull(message = "round 不能为空") Integer round, LocalDateTime plannedAt, String interviewerIds, @NotNull(message = "status 不能为空") String status) {
    }

    /** 更新请求。 */
    public record InterviewUpdateRequest(Long applicationId, Integer round, LocalDateTime plannedAt, String interviewerIds, String status) {
    }

    /** 响应。 */
    public record InterviewResponse(Long id, Long applicationId, Integer round, LocalDateTime plannedAt, String interviewerIds, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

package com.hris.recruit.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Application DTO（由 DDL 自动生成）。
 */
public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    /** 创建请求。 */
    public record ApplicationCreateRequest(@NotNull(message = "candidateId 不能为空") Long candidateId, @NotNull(message = "openingId 不能为空") Long openingId, Long channelId, @NotNull(message = "stage 不能为空") String stage, @NotNull(message = "status 不能为空") String status) {
    }

    /** 更新请求。 */
    public record ApplicationUpdateRequest(Long candidateId, Long openingId, Long channelId, String stage, String status) {
    }

    /** 响应。 */
    public record ApplicationResponse(Long id, Long candidateId, Long openingId, Long channelId, String stage, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

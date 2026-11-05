package com.hris.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Application DTO（由 DDL 自动生成 + RCS-F-012 阶段流转扩展）。
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
    public record ApplicationResponse(Long id, Long candidateId, Long openingId, Long channelId, String stage, String status, String reason, String reasonNote, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /**
     * 阶段流转请求（RCS-F-012）：action=PASS（通过，可推进 toStage 并打标签）/
     * REJECT（淘汰，reason 必填且 OTHER 必填 note）。
     */
    public record ApplicationStageRequest(
            @NotBlank(message = "action 不能为空") String action,
            String toStage,
            String reason,
            String note,
            List<String> tags) {
    }
}

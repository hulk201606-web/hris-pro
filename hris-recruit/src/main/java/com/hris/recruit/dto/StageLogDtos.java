package com.hris.recruit.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * StageLog DTO（由 DDL 自动生成）。
 */
public final class StageLogDtos {

    private StageLogDtos() {
    }

    /** 创建请求。 */
    public record StageLogCreateRequest(@NotNull(message = "applicationId 不能为空") Long applicationId, String fromStage, @NotNull(message = "toStage 不能为空") String toStage, String remark) {
    }

    /** 更新请求。 */
    public record StageLogUpdateRequest(Long applicationId, String fromStage, String toStage, String remark) {
    }

    /** 响应。 */
    public record StageLogResponse(Long id, Long applicationId, String fromStage, String toStage, String remark, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

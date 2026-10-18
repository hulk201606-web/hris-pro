package com.hris.rag.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * EvalRun DTO（由 DDL 自动生成）。
 */
public final class EvalRunDtos {

    private EvalRunDtos() {
    }

    /** 创建请求。 */
    public record EvalRunCreateRequest(@NotNull(message = "evalSetId 不能为空") Long evalSetId, String metrics, @NotNull(message = "status 不能为空") String status) {
    }

    /** 更新请求。 */
    public record EvalRunUpdateRequest(Long evalSetId, String metrics, String status) {
    }

    /** 响应。 */
    public record EvalRunResponse(Long id, Long evalSetId, String metrics, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

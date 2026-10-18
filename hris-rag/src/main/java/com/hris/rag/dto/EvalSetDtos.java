package com.hris.rag.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * EvalSet DTO（由 DDL 自动生成）。
 */
public final class EvalSetDtos {

    private EvalSetDtos() {
    }

    /** 创建请求。 */
    public record EvalSetCreateRequest(@NotNull(message = "name 不能为空") String name, @NotNull(message = "code 不能为空") String code) {
    }

    /** 更新请求。 */
    public record EvalSetUpdateRequest(String name, String code) {
    }

    /** 响应。 */
    public record EvalSetResponse(Long id, String name, String code, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

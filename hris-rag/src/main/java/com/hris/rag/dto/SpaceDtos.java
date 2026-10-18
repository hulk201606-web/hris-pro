package com.hris.rag.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Space DTO（由 DDL 自动生成）。
 */
public final class SpaceDtos {

    private SpaceDtos() {
    }

    /** 创建请求。 */
    public record SpaceCreateRequest(@NotNull(message = "name 不能为空") String name, @NotNull(message = "code 不能为空") String code, String description) {
    }

    /** 更新请求。 */
    public record SpaceUpdateRequest(String name, String code, String description) {
    }

    /** 响应。 */
    public record SpaceResponse(Long id, String name, String code, String description, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

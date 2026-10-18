package com.hris.rag.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * SpaceMember DTO（由 DDL 自动生成）。
 */
public final class SpaceMemberDtos {

    private SpaceMemberDtos() {
    }

    /** 创建请求。 */
    public record SpaceMemberCreateRequest(@NotNull(message = "spaceId 不能为空") Long spaceId, @NotNull(message = "userId 不能为空") Long userId, @NotNull(message = "role 不能为空") String role) {
    }

    /** 更新请求。 */
    public record SpaceMemberUpdateRequest(Long spaceId, Long userId, String role) {
    }

    /** 响应。 */
    public record SpaceMemberResponse(Long id, Long spaceId, Long userId, String role, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

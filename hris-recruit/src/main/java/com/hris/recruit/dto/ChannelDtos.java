package com.hris.recruit.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Channel DTO（由 DDL 自动生成）。
 */
public final class ChannelDtos {

    private ChannelDtos() {
    }

    /** 创建请求。 */
    public record ChannelCreateRequest(@NotNull(message = "name 不能为空") String name, @NotNull(message = "code 不能为空") String code) {
    }

    /** 更新请求。 */
    public record ChannelUpdateRequest(String name, String code) {
    }

    /** 响应。 */
    public record ChannelResponse(Long id, String name, String code, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

package com.hris.platform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 通知渠道偏好 DTO（PLT-F-004）。
 */
public final class NotifyPreferenceDtos {

    private NotifyPreferenceDtos() {
    }

    /**
     * 偏好 upsert 请求（quietStart/quietEnd 缺省 NULL=不启用免打扰；支持跨午夜，
     * 如 22:00-08:00；时间格式 ISO，秒可省略，如 "22:00"）。
     */
    public record PreferenceRequest(
            @NotBlank(message = "channel 不能为空") String channel,
            @NotNull(message = "enabled 不能为空")
            @Min(value = 0, message = "enabled 仅支持 0/1")
            @Max(value = 1, message = "enabled 仅支持 0/1") Integer enabled,
            LocalTime quietStart,
            LocalTime quietEnd) {
    }

    /** 偏好响应 */
    public record PreferenceResponse(
            Long id,
            Long userId,
            String channel,
            Integer enabled,
            LocalTime quietStart,
            LocalTime quietEnd,
            LocalDateTime updatedAt) {
    }
}

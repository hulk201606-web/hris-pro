package com.hris.platform.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 通知中心 DTO（PLT-F-003）。
 */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    /** 模板创建/更新请求 */
    public record TemplateRequest(
            @NotBlank(message = "模板编码不能为空") String code,
            @NotBlank(message = "渠道不能为空") String channel,
            String subject,
            String content) {
    }

    /** 模板响应 */
    public record TemplateResponse(
            Long id,
            String code,
            String channel,
            String subject,
            String content,
            LocalDateTime updatedAt) {
    }

    /** 通知记录响应 */
    public record RecordResponse(
            Long id,
            Long userId,
            Long templateId,
            String channel,
            String sendStatus,
            LocalDateTime sentAt,
            LocalDateTime createdAt) {
    }

    /** 模板渲染结果 */
    public record RenderResponse(String subject, String content) {
    }
}

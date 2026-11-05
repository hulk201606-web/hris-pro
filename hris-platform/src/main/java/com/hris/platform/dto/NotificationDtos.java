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
            String content,
            String webhookUrl) {
    }

    /** 模板响应 */
    public record TemplateResponse(
            Long id,
            String code,
            String channel,
            String subject,
            String content,
            String webhookUrl,
            LocalDateTime updatedAt) {
    }

    /** 模板测试发送请求（webhookUrl 可选，覆盖模板配置的地址） */
    public record TestSendRequest(String webhookUrl) {
    }

    /** 测试发送结果摘要：失败不抛出，以 status/failedReason 表达 */
    public record TestSendResponse(
            Long templateId,
            String channel,
            String webhookUrl,
            String status,
            Integer httpStatus,
            String responseBody,
            String failedReason) {
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

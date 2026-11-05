package com.hris.platform.dto;

/**
 * 平台内部通知 DTO（RCS-F-018 Offer 发送契约）。
 *
 * <p>请求字段以 hris-recruit 的 {@code NotifyClient} 为准：
 * {channel, recipient, subject, content, bizType, bizKey}。响应同时满足任务口径
 * {success, channel, message} 与招聘侧契约 data.status/error。</p>
 */
public final class InternalNotifyDtos {

    private InternalNotifyDtos() {
    }

    /** 内部通知请求。 */
    public record NotifyRequest(
            String channel,
            String recipient,
            String subject,
            String content,
            String bizType,
            String bizKey) {
    }

    /** 内部通知响应：success=false 时 error 给出原因，HTTP 仍为 200。 */
    public record NotifyResponse(
            boolean success,
            String status,
            String channel,
            String message,
            String error,
            Integer httpStatus) {
    }
}

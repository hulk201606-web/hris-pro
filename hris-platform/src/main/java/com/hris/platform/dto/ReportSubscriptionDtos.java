package com.hris.platform.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表订阅 DTO（PLT-F-005）。
 */
public final class ReportSubscriptionDtos {

    private ReportSubscriptionDtos() {
    }

    /** 创建/更新请求：channels 取 EMAIL/WECOM/DINGTALK，recipients 为邮箱或用户 ID。 */
    public record SubscriptionRequest(
            Long reportDefId,
            String cronExpr,
            List<String> channels,
            List<String> recipients,
            Integer status) {
    }

    /** 订阅响应；scheduled 表示调度中心当前是否已装载该任务。 */
    public record SubscriptionResponse(
            Long id,
            Long reportDefId,
            String cronExpr,
            List<String> channels,
            List<String> recipients,
            Integer status,
            String schedulerTaskKey,
            boolean scheduled,
            LocalDateTime updatedAt) {
    }
}

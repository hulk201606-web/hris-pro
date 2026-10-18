package com.hris.platform.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计日志 DTO（PLT-F-008）。
 */
public final class AuditLogDtos {

    private AuditLogDtos() {
    }

    /** 审计日志响应（detail 反序列化为 Map，只读） */
    public record AuditLogResponse(
            Long id,
            Long tenantId,
            Long actorId,
            String action,
            String resource,
            Map<String, Object> detail,
            String ip,
            LocalDateTime createdAt) {
    }
}

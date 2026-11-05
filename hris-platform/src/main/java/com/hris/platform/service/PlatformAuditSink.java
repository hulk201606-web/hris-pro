package com.hris.platform.service;

import com.hris.common.audit.AuditRecord;
import com.hris.common.audit.AuditSink;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * platform 本地审计 Sink（docs/19 §8.7-2）：直调 {@link AuditLogService} 落库，不经 Feign 自调用。
 *
 * <p>@Primary 压过 common AuditSinkConfig 的默认 Feign 装配（该装配在本服务探测不到
 * PlatformAuditClient 时本就为空），保证切面注入唯一 Sink。</p>
 */
@Component
@Primary
public class PlatformAuditSink implements AuditSink {

    private final AuditLogService auditLogService;

    public PlatformAuditSink(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void append(AuditRecord record) {
        auditLogService.append(record.tenantId(), record.userId(), record.action(),
                record.module(), record.detailOrEmpty(), record.ip());
    }
}

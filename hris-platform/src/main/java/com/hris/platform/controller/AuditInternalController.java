package com.hris.platform.controller;

import com.hris.common.audit.AuditRecord;
import com.hris.common.api.R;
import com.hris.platform.service.AuditLogService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计落库内部端点（docs/19 §8.7-2）：各 Servlet 服务经 Feign 调用，追加写入 sys_audit_log。
 *
 * <p>无需 @RequirePerm：/internal/** 被网关 InternalBanWebFilter 封禁公网访问，
 * 仅容器网络内服务间直连可达，防伪造依赖网络边界。tenantId/userId 由调用方
 * 从其 TraceContext 取出后显式传入（写入是全局流，SysAuditLog 跳过租户插件）。</p>
 */
@RestController
@RequestMapping("/internal/plt")
public class AuditInternalController {

    private final AuditLogService auditLogService;

    public AuditInternalController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @PostMapping("/audit")
    public R<Void> append(@RequestBody AuditRecord record) {
        auditLogService.append(record.tenantId(), record.userId(), record.action(),
                record.module(), record.detailOrEmpty(), record.ip());
        return R.ok();
    }
}

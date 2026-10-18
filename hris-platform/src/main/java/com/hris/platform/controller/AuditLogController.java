package com.hris.platform.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.platform.dto.AuditLogDtos.AuditLogResponse;
import com.hris.platform.dto.PageResult;
import com.hris.platform.service.AuditLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 审计日志查询端点（PLT-F-008）。审计角色经网关 perm（plt:audit:read）校验，此处按当前租户隔离。
 */
@RestController
@RequestMapping("/api/v1/plt/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @RequirePerm("plt:audit:read")
    @GetMapping
    public R<PageResult<AuditLogResponse>> query(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(auditLogService.query(actorId, action, from, to, page, size));
    }
}

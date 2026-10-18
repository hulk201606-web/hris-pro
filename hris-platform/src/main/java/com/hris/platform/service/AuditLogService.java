package com.hris.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.platform.dto.AuditLogDtos.AuditLogResponse;
import com.hris.platform.dto.PageResult;
import com.hris.platform.entity.SysAuditLog;
import com.hris.platform.mapper.SysAuditLogMapper;
import com.hris.platform.util.JsonUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审计日志（PLT-F-008，docs/08 §5）：只追加写入 + 租户内查询。
 *
 * <p>实体层已禁用逻辑删/乐观锁（SysAuditLog 不继承 BaseEntity），本服务只提供 insert 与 select，
 * 不暴露任何 update/delete 路径，从代码层保证「只追加」。</p>
 */
@Service
public class AuditLogService {

    private final SysAuditLogMapper mapper;

    public AuditLogService(SysAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    /** 写入审计日志（供各服务 @AuditLog 切面经 MQ/直连调用；写入是全局流，tenant_id 显式传入）。 */
    public void append(Long tenantId, Long actorId, String action, String resource,
                       Map<String, Object> detail, String ip) {
        SysAuditLog e = new SysAuditLog();
        e.setTenantId(tenantId);
        e.setActorId(actorId);
        e.setAction(action);
        e.setResource(resource);
        e.setDetail(JsonUtil.toJson(detail));
        e.setIp(ip);
        e.setCreatedAt(LocalDateTime.now());
        mapper.insert(e);
    }

    /** 从当前请求上下文取 tenant/user 写入。 */
    public void append(String action, String resource, Map<String, Object> detail, String ip) {
        append(TraceContext.getTenantId(), TraceContext.getUserId(), action, resource, detail, ip);
    }

    /** 租户内审计日志查询（审计角色经网关 perm 校验 plt:audit:read；此处显式按当前租户隔离）。 */
    public PageResult<AuditLogResponse> query(Long actorId, String action, LocalDateTime from,
                                              LocalDateTime to, long page, long size) {
        Long tenantId = requireTenant();
        LambdaQueryWrapper<SysAuditLog> qw = new LambdaQueryWrapper<>();
        qw.eq(SysAuditLog::getTenantId, tenantId);
        if (actorId != null) {
            qw.eq(SysAuditLog::getActorId, actorId);
        }
        if (action != null && !action.isBlank()) {
            qw.eq(SysAuditLog::getAction, action.trim());
        }
        if (from != null) {
            qw.ge(SysAuditLog::getCreatedAt, from);
        }
        if (to != null) {
            qw.le(SysAuditLog::getCreatedAt, to);
        }
        qw.orderByDesc(SysAuditLog::getCreatedAt);
        Page<SysAuditLog> p = mapper.selectPage(new Page<>(page, size), qw);
        List<AuditLogResponse> records = p.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, p.getCurrent(), p.getSize(), p.getTotal());
    }

    private Long requireTenant() {
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "缺少租户上下文");
        }
        return tenantId;
    }

    private AuditLogResponse toResponse(SysAuditLog e) {
        return new AuditLogResponse(e.getId(), e.getTenantId(), e.getActorId(), e.getAction(),
                e.getResource(), JsonUtil.toMap(e.getDetail()), e.getIp(), e.getCreatedAt());
    }
}

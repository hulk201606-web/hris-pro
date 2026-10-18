package com.hris.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志（docs/08 §5：只追加、无 UPDATE/DELETE）。
 *
 * <p>不继承 {@link com.hris.common.entity.BaseEntity}：无逻辑删除（deleted）、无乐观锁（version）、
 * 无 updated_by/updated_at——从实体层杜绝物理/逻辑更新。写入是「全局流」（供各服务 @AuditLog 切面
 * 经 MQ/直连落库），故 {@code @InterceptorIgnore(tenantLine = "true")} 跳过租户插件自动注入，
 * tenant_id 由写入方显式赋值，查询侧显式加租户条件（见 AuditLogService）。</p>
 */
@TableName("sys_audit_log")
@InterceptorIgnore(tenantLine = "true")
public class SysAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键：雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户 ID（写入方显式赋值，用于查询侧租户隔离） */
    private Long tenantId;

    /** 操作人用户 ID */
    private Long actorId;

    /** 审计动作 */
    private String action;

    /** 资源标识 */
    private String resource;

    /** 审计明细（JSON 字符串） */
    private String detail;

    /** 来源 IP */
    private String ip;

    /** 创建时间（UTC，分区键，写入方显式赋值） */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

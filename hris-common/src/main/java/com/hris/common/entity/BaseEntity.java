package com.hris.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户业务表公共字段基类（docs/12 §4 全局建模规范）。
 *
 * <p>公共列：id（雪花 ID）、tenant_id、created_by / updated_by、created_at / updated_at、
 * deleted（逻辑删除）、version（乐观锁）。tenant_id 由 MyBatis-Plus 租户插件自动注入
 * （见 {@code com.hris.common.mybatis.MybatisPlusConfig}），其余由 MetaObjectHandler 自动填充。</p>
 *
 * <p>说明：平台级无租户表（如 auth_tenant）不继承本类，需单独定义实体。</p>
 */
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键：雪花 ID（含租户位段），应用层生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户 ID（租户插件自动注入，禁业务代码手写赋值） */
    private Long tenantId;

    /** 创建人 ID，0=系统 */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 更新人 ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 创建时间（UTC，毫秒精度） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（UTC，应用层维护） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除 0=否 1=是 */
    @TableLogic
    private Integer deleted;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

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

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}

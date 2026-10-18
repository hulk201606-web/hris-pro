package com.hris.flow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程模型（flow_ext_model，草稿/部署记录）。
 *
 * <p>注意：本表 DDL 中 {@code version} 列存在两处定义（模型版本 + 乐观锁版本），属基线缺陷
 * （docs/12 §5.8 与 docs/db/schema-mysql.sql 均如此）。实体侧按唯一约束 uk(tenant_id, proc_def_key, version)
 * 的语义，将 {@code version} 解释为「模型版本」（业务字段），本实体不启用乐观锁（不继承 BaseEntity）。
 * 基线 DDL 修正需在迁移阶段去掉重复列（见交付回传风险点）。</p>
 */
@TableName("flow_ext_model")
public class FlowModel implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    /** 流程定义 Key（租户内唯一标识一族版本） */
    private String procDefKey;

    /** 模型版本（业务字段，同 key 递增） */
    private Integer version;

    /** BPMN XML（设计态） */
    private String bpmnXml;

    /** 表单 JSON Schema */
    private String formSchemaJson;

    /** 状态 DRAFT/DEPLOYED */
    private String deployState;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

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

    public String getProcDefKey() {
        return procDefKey;
    }

    public void setProcDefKey(String procDefKey) {
        this.procDefKey = procDefKey;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getBpmnXml() {
        return bpmnXml;
    }

    public void setBpmnXml(String bpmnXml) {
        this.bpmnXml = bpmnXml;
    }

    public String getFormSchemaJson() {
        return formSchemaJson;
    }

    public void setFormSchemaJson(String formSchemaJson) {
        this.formSchemaJson = formSchemaJson;
    }

    public String getDeployState() {
        return deployState;
    }

    public void setDeployState(String deployState) {
        this.deployState = deployState;
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
}

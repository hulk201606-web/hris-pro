package com.hris.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.time.LocalDateTime;

/**
 * rcu_job_opening（由 DDL 自动生成）
 */
@TableName("rcu_job_opening")
public class JobOpening extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long requisitionId;  // 需求ID(逻辑外键 rcu_requisition.id)

    public Long getRequisitionId() {
        return requisitionId;
    }

    public void setRequisitionId(Long requisitionId) {
        this.requisitionId = requisitionId;
    }
    private String code;  // 职位编码

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
    private String title;  // 职位名称

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
    private String description;  // 职位描述

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    private String status;  // 状态 DRAFT/PUBLISHED/CLOSED

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    private LocalDateTime publishAt;  // 发布时间(UTC)

    public LocalDateTime getPublishAt() {
        return publishAt;
    }

    public void setPublishAt(LocalDateTime publishAt) {
        this.publishAt = publishAt;
    }
}

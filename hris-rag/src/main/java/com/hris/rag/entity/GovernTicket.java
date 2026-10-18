package com.hris.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * kb_govern_ticket（由 DDL 自动生成）
 */
@TableName("kb_govern_ticket")
public class GovernTicket extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long docVersionId;  // 文档版本ID(逻辑外键 kb_doc_version.id)

    public Long getDocVersionId() {
        return docVersionId;
    }

    public void setDocVersionId(Long docVersionId) {
        this.docVersionId = docVersionId;
    }
    private String source;  // 来源 FEEDBACK/AUDIT

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
    private String status;  // 状态 OPEN/PROCESSING/CLOSED

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    private String description;  // 工单描述

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

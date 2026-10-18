package com.hris.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.time.LocalDate;

/**
 * kb_doc_version（由 DDL 自动生成）
 */
@TableName("kb_doc_version")
public class DocVersion extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long documentId;  // 文档ID(逻辑外键 kb_document.id)

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }
    private String status;  // 状态 DRAFT/PENDING/PUBLISHED/EXPIRED/ARCHIVED

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    private LocalDate effectiveFrom;  // 生效开始日

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }
    private LocalDate effectiveTo;  // 生效结束日

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }
    private String milvusCollection;  // Milvus Collection名

    public String getMilvusCollection() {
        return milvusCollection;
    }

    public void setMilvusCollection(String milvusCollection) {
        this.milvusCollection = milvusCollection;
    }
}

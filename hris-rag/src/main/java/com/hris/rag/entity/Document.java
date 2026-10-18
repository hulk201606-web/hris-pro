package com.hris.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * kb_document（由 DDL 自动生成）
 */
@TableName("kb_document")
public class Document extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long spaceId;  // 空间ID(逻辑外键 kb_space.id)

    public Long getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
    }
    private String title;  // 文档标题

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
    private String titleHash;  // 标题哈希(去重)

    public String getTitleHash() {
        return titleHash;
    }

    public void setTitleHash(String titleHash) {
        this.titleHash = titleHash;
    }
    private String docType;  // 类型 DOC/FAQ/TABLE

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }
    private Long fileId;  // 源文件ID(逻辑外键 sys_file_meta.id)

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }
}

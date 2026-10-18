package com.hris.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * kb_chunk（由 DDL 自动生成）
 */
@TableName("kb_chunk")
public class Chunk extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long docVersionId;  // 文档版本ID(逻辑外键 kb_doc_version.id)

    public Long getDocVersionId() {
        return docVersionId;
    }

    public void setDocVersionId(Long docVersionId) {
        this.docVersionId = docVersionId;
    }
    private Long spaceId;  // 空间ID(逻辑外键 kb_space.id)

    public Long getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
    }
    private Integer seq;  // 切片序号

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }
    private String headingPath;  // 标题层级路径

    public String getHeadingPath() {
        return headingPath;
    }

    public void setHeadingPath(String headingPath) {
        this.headingPath = headingPath;
    }
    private String text;  // 切片文本(占位; chunk 本体入 Milvus/ES)

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
    private Integer tokenCnt;  // token数

    public Integer getTokenCnt() {
        return tokenCnt;
    }

    public void setTokenCnt(Integer tokenCnt) {
        this.tokenCnt = tokenCnt;
    }
    private String milvusId;  // Milvus向量ID

    public String getMilvusId() {
        return milvusId;
    }

    public void setMilvusId(String milvusId) {
        this.milvusId = milvusId;
    }
    private String esId;  // ES文档ID

    public String getEsId() {
        return esId;
    }

    public void setEsId(String esId) {
        this.esId = esId;
    }
    private String aclTags;  // 权限标签(json)

    public String getAclTags() {
        return aclTags;
    }

    public void setAclTags(String aclTags) {
        this.aclTags = aclTags;
    }
}

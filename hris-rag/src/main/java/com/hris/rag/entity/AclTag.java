package com.hris.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * kb_acl_tag（由 DDL 自动生成）
 */
@TableName("kb_acl_tag")
public class AclTag extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private String name;  // 标签名称

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    private String code;  // 标签编码(密级/部门/角色)

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
    private String tagType;  // 标签类型 SECRET_LEVEL/DEPT/ROLE

    public String getTagType() {
        return tagType;
    }

    public void setTagType(String tagType) {
        this.tagType = tagType;
    }
}

package com.hris.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 租户角色（RBAC）。
 */
@TableName("auth_role")
public class AuthRole extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 角色编码（内置模板或自定义） */
    private String code;

    /** 角色名称 */
    private String name;

    /** 是否内置模板 1=内置 0=自定义 */
    private Integer builtin;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getBuiltin() {
        return builtin;
    }

    public void setBuiltin(Integer builtin) {
        this.builtin = builtin;
    }
}

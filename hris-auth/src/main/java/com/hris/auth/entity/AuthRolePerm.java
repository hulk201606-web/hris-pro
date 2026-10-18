package com.hris.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 角色-权限点（perm_code 格式「模块:资源:动作」）。
 */
@TableName("auth_role_perm")
public class AuthRolePerm extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 角色 ID（逻辑外键 auth_role.id） */
    private Long roleId;

    /** 权限点编码（模块:资源:动作） */
    private String permCode;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getPermCode() {
        return permCode;
    }

    public void setPermCode(String permCode) {
        this.permCode = permCode;
    }
}

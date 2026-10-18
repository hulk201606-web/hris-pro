package com.hris.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 用户-角色关联。
 */
@TableName("auth_user_role")
public class AuthUserRole extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 用户 ID（逻辑外键 auth_user.id） */
    private Long userId;

    /** 角色 ID（逻辑外键 auth_role.id） */
    private Long roleId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
}

package com.hris.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 数据权限规则（docs/08 §2：SELF/DEPT/DEPT_SUB/CUSTOM/ALL）。
 */
@TableName("auth_data_scope")
public class AuthDataScope extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 角色 ID（逻辑外键 auth_role.id） */
    private Long roleId;

    /** 范围 SELF/DEPT/DEPT_SUB/CUSTOM/ALL */
    private String scopeType;

    /** 自定义范围部门 ID 集合（CUSTOM 时），JSON 数组字符串 */
    private String deptIds;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getDeptIds() {
        return deptIds;
    }

    public void setDeptIds(String deptIds) {
        this.deptIds = deptIds;
    }
}

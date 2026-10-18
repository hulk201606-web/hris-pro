package com.hris.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * kb_space_member（由 DDL 自动生成）
 */
@TableName("kb_space_member")
public class SpaceMember extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long spaceId;  // 空间ID(逻辑外键 kb_space.id)

    public Long getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
    }
    private Long userId;  // 用户ID(逻辑外键 auth_user.id)

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
    private String role;  // 成员角色 OWNER/EDITOR/VIEWER

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}

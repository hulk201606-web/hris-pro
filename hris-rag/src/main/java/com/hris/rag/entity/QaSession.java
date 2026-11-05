package com.hris.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * qa_session（问答会话，KBD-F-009，由 DDL 自动生成）
 */
@TableName("qa_session")
public class QaSession extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long userId;  // 用户ID(逻辑外键 auth_user.id)
    private String title;  // 会话标题

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}

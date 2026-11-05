package com.hris.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * qa_feedback（问答反馈，KBD-F-012，由 DDL 自动生成）
 */
@TableName("qa_feedback")
public class QaFeedback extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long messageId;  // 消息ID(逻辑外键 qa_message.id)
    private String type;  // 反馈类型 LIKE/DISLIKE
    private String correctionText;  // 纠错文本

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCorrectionText() {
        return correctionText;
    }

    public void setCorrectionText(String correctionText) {
        this.correctionText = correctionText;
    }
}

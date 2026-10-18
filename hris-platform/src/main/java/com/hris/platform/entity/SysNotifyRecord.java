package com.hris.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 通知记录（PLT-F-003：渲染后的发送记录，状态 PENDING/SENT/FAILED）。
 */
@TableName("sys_notify_record")
public class SysNotifyRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 接收人用户 ID（逻辑外键 auth_user.id） */
    private Long userId;

    /** 模板 ID（逻辑外键 sys_notify_template.id） */
    private Long templateId;

    /** 渠道 */
    private String channel;

    /** 发送状态：PENDING/SENT/FAILED */
    private String sendStatus;

    /** 发送时间（UTC） */
    private LocalDateTime sentAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getSendStatus() {
        return sendStatus;
    }

    public void setSendStatus(String sendStatus) {
        this.sendStatus = sendStatus;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}

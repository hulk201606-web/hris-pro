package com.hris.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 通知模板（PLT-F-003：站内信/邮件/企微多渠道模板，渲染集中在 platform）。
 */
@TableName("sys_notify_template")
public class SysNotifyTemplate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 模板编码（租户内唯一） */
    private String code;

    /** 渠道：EMAIL/SMS/APP/WECOM */
    private String channel;

    /** 标题 */
    private String subject;

    /** 模板内容（支持 {param} 占位符） */
    private String content;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

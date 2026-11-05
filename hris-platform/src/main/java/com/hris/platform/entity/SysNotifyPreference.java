package com.hris.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalTime;

/**
 * 通知渠道偏好（sys_notify_preference，PLT-F-004）。
 *
 * <p>每个用户每个渠道一条（uk tenant_id+user_id+channel）；enabled=0 表示该渠道对
 * 该用户禁用，发送前跳过；quiet_start/quiet_end 为免打扰时段（支持跨午夜）。</p>
 */
@TableName("sys_notify_preference")
public class SysNotifyPreference extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 用户 ID（网关透传 userId，逻辑外键 auth_user.id） */
    private Long userId;

    /** 渠道 DINGTALK/WECOM/WEBHOOK */
    private String channel;

    /** 是否启用 0=禁用 1=启用 */
    private Integer enabled;

    /** 免打扰开始时间（NULL=不启用免打扰） */
    private LocalTime quietStart;

    /** 免打扰结束时间（NULL=不启用免打扰） */
    private LocalTime quietEnd;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public LocalTime getQuietStart() {
        return quietStart;
    }

    public void setQuietStart(LocalTime quietStart) {
        this.quietStart = quietStart;
    }

    public LocalTime getQuietEnd() {
        return quietEnd;
    }

    public void setQuietEnd(LocalTime quietEnd) {
        this.quietEnd = quietEnd;
    }
}

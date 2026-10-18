package com.hris.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 全局事件表（事务消息模板，docs/12 §5.10）。平台级无租户表，无 tenant_id 列。
 *
 * <p>{@code @InterceptorIgnore(tenantLine = "true")} 跳过租户插件，避免对无 tenant_id 列的表注入条件。
 * 用于「本地事务 + 事件表」的发件箱模式：业务写库同事务 enqueue，独立调度器按 state 轮询投递 MQ。</p>
 */
@TableName("sys_outbox_message")
@InterceptorIgnore(tenantLine = "true")
public class SysOutboxMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键：雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 事件 ID（幂等，全局唯一） */
    private String eventId;

    /** MQ Topic */
    private String topic;

    /** 事件载荷（JSON 字符串） */
    private String payload;

    /** 状态：PENDING/SENT/DEAD */
    private String state;

    /** 重试次数 */
    private Integer retryCnt;

    /** 下次重试时间（UTC） */
    private LocalDateTime nextRetryAt;

    /** 创建时间（UTC） */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Integer getRetryCnt() {
        return retryCnt;
    }

    public void setRetryCnt(Integer retryCnt) {
        this.retryCnt = retryCnt;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

package com.hris.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 调度任务执行日志（PLT-F-011：任务看板与失败重试的可观测依据）。
 *
 * <p>task_key / handler 采用冗余而非外键：注册项被删除后，历史执行记录仍要能追溯
 * 「哪个任务、哪个处理器、失败原因是什么」。审计类数据不随业务对象一起消失。</p>
 */
@TableName("sys_scheduler_task_log")
public class SysSchedulerTaskLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 注册任务 ID（手动执行或注册项已删除时可能为空） */
    private Long taskId;

    /** 任务键（冗余） */
    private String taskKey;

    /** 处理器标识（冗余） */
    private String handler;

    /** 触发方式：CRON / MANUAL / RETRY */
    private String triggerType;

    /** 结果：SUCCESS / FAILED */
    private String status;

    /** 第几次重试，0=首次执行 */
    private Integer retryNo;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 执行摘要或失败原因 */
    private String message;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRetryNo() {
        return retryNo;
    }

    public void setRetryNo(Integer retryNo) {
        this.retryNo = retryNo;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

package com.hris.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 调度任务注册（PLT-F-011：XXL-JOB/SchedulerX 任务注册表，审计分区维护任务等）。
 */
@TableName("sys_scheduler_task_registry")
public class SysSchedulerTaskRegistry extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务键（租户内唯一） */
    private String taskKey;

    /** cron 表达式 */
    private String cron;

    /** 处理器标识 */
    private String handler;

    /** 状态：1=启用 0=停用 */
    private Integer status;

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

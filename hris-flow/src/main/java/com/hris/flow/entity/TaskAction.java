package com.hris.flow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 审批动作明细（flow_ext_task_action，docs/04 §5）。
 * 索引 idx(tenant_id, task_id, created_at)。
 */
@TableName("flow_ext_task_action")
public class TaskAction extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务 ID（逻辑外键 ACT_RU_TASK.ID_） */
    private String taskId;

    /** 动作 APPROVE/REJECT/TRANSFER/ADD_SIGN/WITHDRAW */
    private String action;

    /** 操作人用户 ID（逻辑外键 auth_user.id） */
    private Long operator;

    /** 审批意见 */
    private String comment;

    /** 附件（JSON 字符串） */
    private String attachments;
    /** 动态表单值 JSON（键=字段名，docs/19 §10.5-C11） */
    private String formValues;

    /** 委托来源用户 ID（加签/转办链） */
    private Long delegateFrom;

    /** 动作时间（UTC） */
    private LocalDateTime ts;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getOperator() {
        return operator;
    }

    public void setOperator(Long operator) {
        this.operator = operator;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getAttachments() {
        return attachments;
    }

    public String getFormValues() {
        return formValues;
    }

    public void setFormValues(String formValues) {
        this.formValues = formValues;
    }

    public void setAttachments(String attachments) {
        this.attachments = attachments;
    }

    public Long getDelegateFrom() {
        return delegateFrom;
    }

    public void setDelegateFrom(Long delegateFrom) {
        this.delegateFrom = delegateFrom;
    }

    public LocalDateTime getTs() {
        return ts;
    }

    public void setTs(LocalDateTime ts) {
        this.ts = ts;
    }
}

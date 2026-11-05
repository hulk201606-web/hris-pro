package com.hris.flow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 任务结构化审批意见标签（flow_ext_task_opinion_tag，FLW-F-020）。
 *
 * <p>与 flow_ext_task_action.comment 的关系：comment 是自由文本，本表是「标签 + 可选说明」的
 * 结构化留痕，供轨迹展示与统计（如「要求补材料」占比）。同一任务可多次打标（追加审计）。</p>
 */
@TableName("flow_ext_task_opinion_tag")
public class TaskOpinionTag extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务 ID（逻辑外键 ACT_RU_TASK.ID_） */
    private String taskId;

    /** 标签枚举（RECHECK_DATA/NEED_MORE_INFO/COMPLIANT/OTHER） */
    private String tag;

    /** 标签说明（可选） */
    private String comment;

    /** 打标人用户 ID（逻辑外键 auth_user.id） */
    private Long operator;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Long getOperator() {
        return operator;
    }

    public void setOperator(Long operator) {
        this.operator = operator;
    }
}

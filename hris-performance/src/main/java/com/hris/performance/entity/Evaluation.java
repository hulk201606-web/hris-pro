package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * 评估结果（域：绩效，分层：实体/DO）。单次评估任务的评分与评语 JSON。
 */
@TableName("prf_evaluation")
public class Evaluation extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long taskId;  // 评估任务ID(逻辑外键 prf_eval_task.id)

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }
    private Long evaluatorId;  // 评价人用户ID(逻辑外键 auth_user.id)

    public Long getEvaluatorId() {
        return evaluatorId;
    }

    public void setEvaluatorId(Long evaluatorId) {
        this.evaluatorId = evaluatorId;
    }
    private String scores;  // 评分明细(json)

    public String getScores() {
        return scores;
    }

    public void setScores(String scores) {
        this.scores = scores;
    }
    private String comment;  // 评语

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}

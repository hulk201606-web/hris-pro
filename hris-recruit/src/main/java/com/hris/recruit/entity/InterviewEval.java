package com.hris.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * rcu_interview_eval（由 DDL 自动生成）
 */
@TableName("rcu_interview_eval")
public class InterviewEval extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long interviewId;  // 面试ID(逻辑外键 rcu_interview.id)

    public Long getInterviewId() {
        return interviewId;
    }

    public void setInterviewId(Long interviewId) {
        this.interviewId = interviewId;
    }
    private Long evaluatorId;  // 评价人用户ID(逻辑外键 auth_user.id)

    public Long getEvaluatorId() {
        return evaluatorId;
    }

    public void setEvaluatorId(Long evaluatorId) {
        this.evaluatorId = evaluatorId;
    }
    private Integer score;  // 评分

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }
    private String comment;  // 评价意见

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
    private Integer pass;  // 是否通过 1=通过 0=不通过

    public Integer getPass() {
        return pass;
    }

    public void setPass(Integer pass) {
        this.pass = pass;
    }
}

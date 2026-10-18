package com.hris.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.time.LocalDateTime;

/**
 * rcu_interview（由 DDL 自动生成）
 */
@TableName("rcu_interview")
public class Interview extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long applicationId;  // 投递ID(逻辑外键 rcu_application.id)

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }
    private Integer round;  // 面试轮次

    public Integer getRound() {
        return round;
    }

    public void setRound(Integer round) {
        this.round = round;
    }
    private LocalDateTime plannedAt;  // 计划面试时间(UTC)

    public LocalDateTime getPlannedAt() {
        return plannedAt;
    }

    public void setPlannedAt(LocalDateTime plannedAt) {
        this.plannedAt = plannedAt;
    }
    private String interviewerIds;  // 面试官ID集合

    public String getInterviewerIds() {
        return interviewerIds;
    }

    public void setInterviewerIds(String interviewerIds) {
        this.interviewerIds = interviewerIds;
    }
    private String status;  // 状态 SCHEDULED/DONE/CANCELLED

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

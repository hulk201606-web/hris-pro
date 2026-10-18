package com.hris.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * rcu_application（由 DDL 自动生成）
 */
@TableName("rcu_application")
public class Application extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long candidateId;  // 候选人ID(逻辑外键 rcu_candidate.id)

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }
    private Long openingId;  // 职位ID(逻辑外键 rcu_job_opening.id)

    public Long getOpeningId() {
        return openingId;
    }

    public void setOpeningId(Long openingId) {
        this.openingId = openingId;
    }
    private Long channelId;  // 渠道ID(逻辑外键 rcu_channel.id)

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }
    private String stage;  // 阶段 APPLIED/SCREEN/INTERVIEW/OFFER/HIRED

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }
    private String status;  // 状态 ACTIVE/REJECTED/WITHDRAWN

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

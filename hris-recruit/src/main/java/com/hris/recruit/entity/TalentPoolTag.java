package com.hris.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * rcu_talent_pool_tag（由 DDL 自动生成）
 */
@TableName("rcu_talent_pool_tag")
public class TalentPoolTag extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long candidateId;  // 候选人ID(逻辑外键 rcu_candidate.id)

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }
    private String tag;  // 标签

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }
}

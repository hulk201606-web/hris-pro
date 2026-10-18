package com.hris.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * rcu_stage_log（由 DDL 自动生成）
 */
@TableName("rcu_stage_log")
public class StageLog extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long applicationId;  // 投递ID(逻辑外键 rcu_application.id)

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }
    private String fromStage;  // 原阶段

    public String getFromStage() {
        return fromStage;
    }

    public void setFromStage(String fromStage) {
        this.fromStage = fromStage;
    }
    private String toStage;  // 新阶段

    public String getToStage() {
        return toStage;
    }

    public void setToStage(String toStage) {
        this.toStage = toStage;
    }
    private String remark;  // 备注

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}

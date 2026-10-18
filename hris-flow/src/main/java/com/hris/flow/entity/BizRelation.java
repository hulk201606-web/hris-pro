package com.hris.flow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 业务单据-流程实例映射（flow_ext_biz_relation）。
 * 唯一约束 uk(tenant_id, biz_type, biz_key) 作为发起幂等锁（docs/04 §2）。
 */
@TableName("flow_ext_biz_relation")
public class BizRelation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流程实例 ID（逻辑外键 ACT_HI_PROCINST.ID_） */
    private String procInstId;

    /** 业务类型（如 ATT_LEAVE，兼作流程定义 Key） */
    private String bizType;

    /** 业务单号（租户内唯一） */
    private String bizKey;

    /** 状态 RUNNING/FINISHED/TERMINATED */
    private String status;

    public String getProcInstId() {
        return procInstId;
    }

    public void setProcInstId(String procInstId) {
        this.procInstId = procInstId;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

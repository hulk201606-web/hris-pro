package com.hris.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 关键信息变更申请（emp_profile_change，EMP-F-021）。
 *
 * <p>自助端拒绝直改的关键字段（证件/姓名/银行卡/雇佣类型）以申请单形式发起
 * EMP_PROFILE_CHG 流程；审批回调按 bizKey 找回本行并落账，幂等：非 PENDING 不重复应用。</p>
 */
@TableName("emp_profile_change")
public class EmpProfileChange extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID */
    private Long empId;

    /** 流程业务键 */
    private String bizKey;

    /** 待生效字段变更 JSON */
    private String changesJson;

    /** 状态 PENDING/APPLIED/REJECTED */
    private String status;

    /** 流程实例 ID */
    private String procInstId;

    /** 申请原因 */
    private String reason;

    /** 落账时间 */
    private LocalDateTime appliedAt;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public String getChangesJson() {
        return changesJson;
    }

    public void setChangesJson(String changesJson) {
        this.changesJson = changesJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProcInstId() {
        return procInstId;
    }

    public void setProcInstId(String procInstId) {
        this.procInstId = procInstId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }
}

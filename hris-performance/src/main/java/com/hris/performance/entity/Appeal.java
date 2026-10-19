package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * 绩效申诉（域：绩效，分层：实体/DO）。员工对评估结果发起申诉，走审批流，状态机 OPEN→PROCESSING→RESOLVED/REJECTED。
 */
@TableName("prf_appeal")
public class Appeal extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long empId;  // 员工ID(逻辑外键 emp_employee.id)

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }
    private Long cycleId;  // 周期ID(逻辑外键 prf_cycle.id)

    public Long getCycleId() {
        return cycleId;
    }

    public void setCycleId(Long cycleId) {
        this.cycleId = cycleId;
    }
    private String bizKey;  // 业务单号(流程幂等键)

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }
    private String reason;  // 申诉理由

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
    private String status;  // 状态 DRAFT/PROCESSING/APPROVED/REJECTED

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    private String procInstId;  // 流程实例ID

    public String getProcInstId() {
        return procInstId;
    }

    public void setProcInstId(String procInstId) {
        this.procInstId = procInstId;
    }
}

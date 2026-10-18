package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * prf_eval_task（由 DDL 自动生成）
 */
@TableName("prf_eval_task")
public class EvalTask extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long cycleId;  // 周期ID(逻辑外键 prf_cycle.id)

    public Long getCycleId() {
        return cycleId;
    }

    public void setCycleId(Long cycleId) {
        this.cycleId = cycleId;
    }
    private Long empId;  // 被评估人员工ID(逻辑外键 emp_employee.id)

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }
    private String evaluatorRole;  // 评价人角色 SELF/SUPERVISOR/PEER/SUBORDINATE

    public String getEvaluatorRole() {
        return evaluatorRole;
    }

    public void setEvaluatorRole(String evaluatorRole) {
        this.evaluatorRole = evaluatorRole;
    }
    private String status;  // 状态 PENDING/DONE

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

package com.hris.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 编制占用流水（ORG-F-010）。
 */
@TableName("org_hc_occupancy")
public class OrgHcOccupancy extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    /** 招聘需求 ID（逻辑外键 rcu_requisition.id） */
    private Long requisitionId;

    /** 变动类型 OCCUPY/RELEASE/TRANSFER */
    private String changeType;

    /** 部门 ID（逻辑外键 org_dept.id） */
    private Long deptId;

    /** 岗位 ID（逻辑外键 org_position.id） */
    private Long positionId;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public Long getRequisitionId() {
        return requisitionId;
    }

    public void setRequisitionId(Long requisitionId) {
        this.requisitionId = requisitionId;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }
}

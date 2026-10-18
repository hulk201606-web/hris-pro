package com.hris.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * rcu_requisition（由 DDL 自动生成）
 */
@TableName("rcu_requisition")
public class Requisition extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private String reqNo;  // 需求编号

    public String getReqNo() {
        return reqNo;
    }

    public void setReqNo(String reqNo) {
        this.reqNo = reqNo;
    }
    private Long deptId;  // 部门ID(逻辑外键 org_dept.id)

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }
    private Long positionId;  // 岗位ID(逻辑外键 org_position.id)

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }
    private Integer headcount;  // 招聘人数

    public Integer getHeadcount() {
        return headcount;
    }

    public void setHeadcount(Integer headcount) {
        this.headcount = headcount;
    }
    private String reason;  // 需求原因

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
    private String status;  // 状态 DRAFT/APPROVING/OPEN/CLOSED

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

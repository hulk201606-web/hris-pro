package com.hris.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 编制（部门×岗位×周期，ORG-F-009）。
 */
@TableName("org_headcount")
public class OrgHeadcount extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 部门 ID（逻辑外键 org_dept.id） */
    private Long deptId;

    /** 岗位 ID（逻辑外键 org_position.id） */
    private Long positionId;

    /** 编制周期（如 2026Q1） */
    private String period;

    /** 编制数 */
    private Integer quota;

    /** 已占用数 */
    private Integer occupied;

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

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public Integer getQuota() {
        return quota;
    }

    public void setQuota(Integer quota) {
        this.quota = quota;
    }

    public Integer getOccupied() {
        return occupied;
    }

    public void setOccupied(Integer occupied) {
        this.occupied = occupied;
    }
}

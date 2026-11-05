package com.hris.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 岗位 × 部门挂靠（ORG-F-006 深化：岗位可在多部门挂靠，uk 三元组 tenant+position+dept）。
 */
@TableName("org_position_dept")
public class OrgPositionDept extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 岗位 ID（逻辑外键 org_position.id） */
    private Long positionId;

    /** 部门 ID（逻辑外键 org_dept.id） */
    private Long deptId;

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }
}

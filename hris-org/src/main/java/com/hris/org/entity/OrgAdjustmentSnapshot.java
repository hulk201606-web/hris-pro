package com.hris.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 组织调整申请单快照（独立表，根治 org_dept_history 快照污染，docs/19 §10.5-B7）。
 *
 * <p>申请单 JSON（kind=ORG_ADJUST_REQUEST）落本表，org_dept_history 回归纯历史快照语义。</p>
 */
@TableName("org_adjustment_snapshot")
public class OrgAdjustmentSnapshot extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 调整申请 ID（雪花 ID，对应流程业务键 bizKey=ORG_ADJUST:{adjustment_id}） */
    private Long adjustmentId;

    /** 部门 ID（逻辑外键 org_dept.id，CREATE 类申请为 0） */
    private Long deptId;

    /** 申请单 JSON（含状态/载荷/审批意见） */
    private String snapshotJson;

    public Long getAdjustmentId() {
        return adjustmentId;
    }

    public void setAdjustmentId(Long adjustmentId) {
        this.adjustmentId = adjustmentId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }
}

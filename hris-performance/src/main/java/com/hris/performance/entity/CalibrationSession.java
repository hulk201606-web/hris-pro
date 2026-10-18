package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * prf_calibration_session（由 DDL 自动生成）
 */
@TableName("prf_calibration_session")
public class CalibrationSession extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long cycleId;  // 周期ID(逻辑外键 prf_cycle.id)

    public Long getCycleId() {
        return cycleId;
    }

    public void setCycleId(Long cycleId) {
        this.cycleId = cycleId;
    }
    private Long deptId;  // 部门ID(逻辑外键 org_dept.id)

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }
    private String status;  // 状态 DRAFT/IN_PROGRESS/DONE

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

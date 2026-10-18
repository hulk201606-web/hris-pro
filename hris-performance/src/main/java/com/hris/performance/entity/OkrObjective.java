package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * prf_okr_objective（由 DDL 自动生成）
 */
@TableName("prf_okr_objective")
public class OkrObjective extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long cycleId;  // 周期ID(逻辑外键 prf_cycle.id)

    public Long getCycleId() {
        return cycleId;
    }

    public void setCycleId(Long cycleId) {
        this.cycleId = cycleId;
    }
    private Long empId;  // 员工ID(逻辑外键 emp_employee.id)

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }
    private String objective;  // 目标O

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }
    private String keyResults;  // 关键结果KR列表(json)

    public String getKeyResults() {
        return keyResults;
    }

    public void setKeyResults(String keyResults) {
        this.keyResults = keyResults;
    }
}

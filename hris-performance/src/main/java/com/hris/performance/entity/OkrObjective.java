package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * OKR 目标（域：绩效，分层：实体/DO）。员工周期内的 O 与 KR 列表（JSON），与 Goal 并行支持 OKR 考核模式。
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

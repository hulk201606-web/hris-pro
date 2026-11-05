package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 算薪任务（域：薪酬，分层：实体/DO，CMP-F-006 逐员工监控）。
 * 单员工单批次算薪任务行：status=SUCCESS/FAILED/SKIPPED（RUNNING 为批次内瞬时态），
 * error 记录失败原因（公式+变量快照见 items_json，可复算定位）。
 */
@TableName("cmp_calc_task")
public class CalcTask extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long runId;  // 算薪批次ID(逻辑外键 cmp_payroll_run.id)

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }
    private Long empId;  // 员工ID(逻辑外键 emp_employee.id)

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }
    private String status;  // 任务状态 RUNNING/SUCCESS/FAILED/SKIPPED

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    private String error;  // 失败原因(FAILED 时)

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
    private String itemsJson;  // 逐项求值结果快照(json,可复算)

    public String getItemsJson() {
        return itemsJson;
    }

    public void setItemsJson(String itemsJson) {
        this.itemsJson = itemsJson;
    }
}

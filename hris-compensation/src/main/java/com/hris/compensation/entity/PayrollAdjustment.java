package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.math.BigDecimal;

/**
 * 算薪调整项（域：薪酬，分层：实体/DO，密域）。算薪批次中的手工补款/扣款，amount 为密文存储。
 */
@TableName("cmp_payroll_adjustment")
public class PayrollAdjustment extends BaseEntity {

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
    private String adjustType;  // 调整类型

    public String getAdjustType() {
        return adjustType;
    }

    public void setAdjustType(String adjustType) {
        this.adjustType = adjustType;
    }
    private BigDecimal amount;  // 调整金额

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    private String reason;  // 调整原因

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

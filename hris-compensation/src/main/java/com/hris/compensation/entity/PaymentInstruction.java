package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.time.LocalDateTime;

/**
 * 发放指令（域：薪酬，分层：实体/DO）。算薪完成后生成的银行发放指令，状态机 PENDING→PAID/FAILED。
 */
@TableName("cmp_payment_instruction")
public class PaymentInstruction extends BaseEntity {

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
    private String payStatus;  // 发放状态 PENDING/SUCCESS/FAILED

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }
    private LocalDateTime paidAt;  // 发放时间(UTC)

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }
}

package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * cmp_payroll_detail（由 DDL 自动生成）
 */
@TableName("cmp_payroll_detail")
public class PayrollDetail extends BaseEntity {

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
    private String inputSnapshot;  // 算薪输入快照(json,可复算)

    public String getInputSnapshot() {
        return inputSnapshot;
    }

    public void setInputSnapshot(String inputSnapshot) {
        this.inputSnapshot = inputSnapshot;
    }
    private String amountCipher;  // 应发金额密文(逻辑列 amount)

    public String getAmountCipher() {
        return amountCipher;
    }

    public void setAmountCipher(String amountCipher) {
        this.amountCipher = amountCipher;
    }
    private String amountMask;  // 应发金额掩码

    public String getAmountMask() {
        return amountMask;
    }

    public void setAmountMask(String amountMask) {
        this.amountMask = amountMask;
    }
    private String netAmountCipher;  // 实发金额密文(逻辑列 net_amount)

    public String getNetAmountCipher() {
        return netAmountCipher;
    }

    public void setNetAmountCipher(String netAmountCipher) {
        this.netAmountCipher = netAmountCipher;
    }
    private String netAmountMask;  // 实发金额掩码

    public String getNetAmountMask() {
        return netAmountMask;
    }

    public void setNetAmountMask(String netAmountMask) {
        this.netAmountMask = netAmountMask;
    }
}

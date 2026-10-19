package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.time.LocalDate;

/**
 * 员工薪资档案（域：薪酬，分层：实体/DO，密域）。记录员工定薪金额与银行账号，密级：salaryAmountCipher/bankAccountCipher 为密文存储，非授权视图仅返回 salaryAmountMask/bankAccountMask 掩码；读取须具备 cmp:salary:read 权限。
 */
@TableName("cmp_employee_pay")
public class EmployeePay extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long empId;  // 员工ID(逻辑外键 emp_employee.id)

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }
    private Long payGroupId;  // 薪资组ID(逻辑外键 cmp_pay_group.id)

    public Long getPayGroupId() {
        return payGroupId;
    }

    public void setPayGroupId(Long payGroupId) {
        this.payGroupId = payGroupId;
    }
    private Long payGradeId;  // 薪级ID(逻辑外键 cmp_pay_grade.id)

    public Long getPayGradeId() {
        return payGradeId;
    }

    public void setPayGradeId(Long payGradeId) {
        this.payGradeId = payGradeId;
    }
    private LocalDate effectiveFrom;  // 生效开始日

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }
    private LocalDate effectiveTo;  // 生效结束日(NULL=现行)

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }
    private String salaryAmountCipher;  // 定薪金额密文(逻辑列 salary_amount)

    public String getSalaryAmountCipher() {
        return salaryAmountCipher;
    }

    public void setSalaryAmountCipher(String salaryAmountCipher) {
        this.salaryAmountCipher = salaryAmountCipher;
    }
    private String salaryAmountMask;  // 定薪金额掩码

    public String getSalaryAmountMask() {
        return salaryAmountMask;
    }

    public void setSalaryAmountMask(String salaryAmountMask) {
        this.salaryAmountMask = salaryAmountMask;
    }
    private String bankAccountCipher;  // 银行账号密文(逻辑列 bank_account)

    public String getBankAccountCipher() {
        return bankAccountCipher;
    }

    public void setBankAccountCipher(String bankAccountCipher) {
        this.bankAccountCipher = bankAccountCipher;
    }
    private String bankAccountMask;  // 银行账号掩码

    public String getBankAccountMask() {
        return bankAccountMask;
    }

    public void setBankAccountMask(String bankAccountMask) {
        this.bankAccountMask = bankAccountMask;
    }
}

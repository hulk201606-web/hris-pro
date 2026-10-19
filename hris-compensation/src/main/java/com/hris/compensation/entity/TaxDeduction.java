package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.time.LocalDate;

/**
 * 个税专项附加扣除（域：薪酬，分层：实体/DO，密域）。员工个税专项附加扣除信息，deductionAmountCipher 为密文存储。
 */
@TableName("cmp_tax_deduction")
public class TaxDeduction extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long empId;  // 员工ID(逻辑外键 emp_employee.id)

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }
    private LocalDate effectiveFrom;  // 生效开始日

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }
    private LocalDate effectiveTo;  // 生效结束日

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }
    private String deductionAmountCipher;  // 专项附加扣除金额密文(逻辑列 deduction_amount)

    public String getDeductionAmountCipher() {
        return deductionAmountCipher;
    }

    public void setDeductionAmountCipher(String deductionAmountCipher) {
        this.deductionAmountCipher = deductionAmountCipher;
    }
    private String deductionAmountMask;  // 专项附加扣除金额掩码

    public String getDeductionAmountMask() {
        return deductionAmountMask;
    }

    public void setDeductionAmountMask(String deductionAmountMask) {
        this.deductionAmountMask = deductionAmountMask;
    }
}

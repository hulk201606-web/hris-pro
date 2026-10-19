package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 社保公积金档案（域：薪酬，分层：实体/DO）。员工社保/公积金缴纳基数与比例配置。
 */
@TableName("cmp_social_fund_profile")
public class SocialFundProfile extends BaseEntity {

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
    private String fundType;  // 类型 SOCIAL/HOUSING_FUND

    public String getFundType() {
        return fundType;
    }

    public void setFundType(String fundType) {
        this.fundType = fundType;
    }
    private BigDecimal baseAmount;  // 缴纳基数

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
    }
}

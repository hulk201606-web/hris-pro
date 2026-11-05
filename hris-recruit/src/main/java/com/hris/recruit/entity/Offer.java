package com.hris.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * rcu_offer（由 DDL 自动生成）
 */
@TableName(value = "rcu_offer", autoResultMap = true)
public class Offer extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long applicationId;  // 投递ID(逻辑外键 rcu_application.id)

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }
    private String offerNo;  // Offer编号

    public String getOfferNo() {
        return offerNo;
    }

    public void setOfferNo(String offerNo) {
        this.offerNo = offerNo;
    }
    private Integer salaryBandCheck;  // 薪资带校验结果 1=通过 0=超带

    public Integer getSalaryBandCheck() {
        return salaryBandCheck;
    }

    public void setSalaryBandCheck(Integer salaryBandCheck) {
        this.salaryBandCheck = salaryBandCheck;
    }
    @TableField(typeHandler = com.hris.common.crypto.CipherTypeHandler.RcuOfferSalary.class)
    private String salaryAmountCipher;  // Offer薪资密文(逻辑列 salary_amount)

    public String getSalaryAmountCipher() {
        return salaryAmountCipher;
    }

    public void setSalaryAmountCipher(String salaryAmountCipher) {
        this.salaryAmountCipher = salaryAmountCipher;
    }
    private String salaryAmountMask;  // Offer薪资掩码

    public String getSalaryAmountMask() {
        return salaryAmountMask;
    }

    public void setSalaryAmountMask(String salaryAmountMask) {
        this.salaryAmountMask = salaryAmountMask;
    }
    private String status;  // 状态 DRAFT/SENT/ACCEPTED/REJECTED

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    private String procInstId;  // 流程实例ID

    public String getProcInstId() {
        return procInstId;
    }

    public void setProcInstId(String procInstId) {
        this.procInstId = procInstId;
    }
}

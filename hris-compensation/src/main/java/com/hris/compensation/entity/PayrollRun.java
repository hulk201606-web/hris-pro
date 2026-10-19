package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * 算薪批次（域：薪酬，分层：实体/DO）。状态机 DRAFT→CALCULATING→LOCKED→PAID→ARCHIVED，锁单后不可修改。
 */
@TableName("cmp_payroll_run")
public class PayrollRun extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private String periodCode;  // 周期编码(如 2026-01)

    public String getPeriodCode() {
        return periodCode;
    }

    public void setPeriodCode(String periodCode) {
        this.periodCode = periodCode;
    }
    private Long payGroupId;  // 薪资组ID(逻辑外键 cmp_pay_group.id)

    public Long getPayGroupId() {
        return payGroupId;
    }

    public void setPayGroupId(Long payGroupId) {
        this.payGroupId = payGroupId;
    }
    private String status;  // 状态 INIT/TRIAL/CALC/REVIEW/APPROVE/PAID/LOCK

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    private Long calcBy;  // 计算人ID

    public Long getCalcBy() {
        return calcBy;
    }

    public void setCalcBy(Long calcBy) {
        this.calcBy = calcBy;
    }
    private Long reviewBy;  // 复核人ID

    public Long getReviewBy() {
        return reviewBy;
    }

    public void setReviewBy(Long reviewBy) {
        this.reviewBy = reviewBy;
    }
}

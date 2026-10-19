package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.math.BigDecimal;

/**
 * 月结调整项（域：考勤，分层：实体/DO）。月结锁账后唯一可影响下期的差异通道（红冲语义），记录补款/扣款类型与金额。
 */
@TableName("att_closing_adjustment")
public class ClosingAdjustment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    /** 月结批次 ID（逻辑外键 att_monthly_closing.id） */
    private Long closingId;

    /** 调整类型（如 LATE_DEDUCT / LEAVE_FIX / OT_FIX） */
    private String adjustType;

    /** 调整量（小时或天，按 adjust_type 语义） */
    private BigDecimal amount;

    /** 调整原因 */
    private String reason;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public Long getClosingId() {
        return closingId;
    }

    public void setClosingId(Long closingId) {
        this.closingId = closingId;
    }

    public String getAdjustType() {
        return adjustType;
    }

    public void setAdjustType(String adjustType) {
        this.adjustType = adjustType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

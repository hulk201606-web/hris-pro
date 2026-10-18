package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.math.BigDecimal;

/**
 * 假期额度流水（att_leave_ledger）。额度的每一次增减都留痕，供对账与追溯。
 *
 * <p>change_amount 正增负减（天）；biz_ref 回填业务单号（请假单 biz_key）（docs/12 §5.4）。</p>
 */
@TableName("att_leave_ledger")
public class LeaveLedger extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    /** 假种 ID（逻辑外键 att_leave_type.id） */
    private Long leaveTypeId;

    /** 变动额度（天，正增负减） */
    private BigDecimal changeAmount;

    /** 业务引用（请假单号） */
    private String bizRef;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public Long getLeaveTypeId() {
        return leaveTypeId;
    }

    public void setLeaveTypeId(Long leaveTypeId) {
        this.leaveTypeId = leaveTypeId;
    }

    public BigDecimal getChangeAmount() {
        return changeAmount;
    }

    public void setChangeAmount(BigDecimal changeAmount) {
        this.changeAmount = changeAmount;
    }

    public String getBizRef() {
        return bizRef;
    }

    public void setBizRef(String bizRef) {
        this.bizRef = bizRef;
    }
}

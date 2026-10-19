package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.math.BigDecimal;

/**
 * 假期额度（域：考勤，分层：实体/DO）。员工某假期类型在某周期的总额度与已用额度，按年/季重置。
 */
@TableName("att_leave_quota")
public class LeaveQuota extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    /** 假种 ID（逻辑外键 att_leave_type.id） */
    private Long leaveTypeId;

    /** 周期（如 2026） */
    private String period;

    /** 总额度（天） */
    private BigDecimal totalQuota;

    /** 已用额度（天） */
    private BigDecimal usedQuota;

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

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public BigDecimal getTotalQuota() {
        return totalQuota;
    }

    public void setTotalQuota(BigDecimal totalQuota) {
        this.totalQuota = totalQuota;
    }

    public BigDecimal getUsedQuota() {
        return usedQuota;
    }

    public void setUsedQuota(BigDecimal usedQuota) {
        this.usedQuota = usedQuota;
    }
}

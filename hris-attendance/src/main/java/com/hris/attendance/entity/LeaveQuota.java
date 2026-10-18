package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.math.BigDecimal;

/**
 * 假期额度（att_leave_quota）。员工在某周期（如 2026）某假种下的额度余额。
 *
 * <p>uk(tenant_id, emp_id, leave_type_id, period) 保证同一周期仅一条额度行，
 * 发放走 upsert 累加（docs/12 §5.4）。</p>
 *
 * <p>注意：DDL 无 frozen_quota 列，MVP 不支持审批期冻结（见 OPEN-DECISIONS）。</p>
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

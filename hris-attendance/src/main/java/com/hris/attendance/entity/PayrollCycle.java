package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 薪酬取数周期（att_payroll_cycle）。支持自然月、上月 21 日至本月 20 日等切分方式。
 *
 * <p>uk(tenant_id, period_code) 保证周期编码唯一；月结以 period_code 关联（docs/12 §5.4）。</p>
 */
@TableName("att_payroll_cycle")
public class PayrollCycle extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 周期编码（如 2026-01） */
    private String periodCode;

    /** 周期开始日 */
    private LocalDate startDate;

    /** 周期结束日 */
    private LocalDate endDate;

    /** 状态 OPEN/CLOSED */
    private String status;

    public String getPeriodCode() {
        return periodCode;
    }

    public void setPeriodCode(String periodCode) {
        this.periodCode = periodCode;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 薪资核算周期（域：考勤，分层：实体/DO）。定义算薪月份的起止日期与状态，是考勤月结与薪酬算薪的时间锚点。
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

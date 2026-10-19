package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 月结批次（域：考勤，分层：实体/DO）。状态机 INIT→RUNNING→DONE/FAILED，锁账后历史日考勤不可改，供薪酬域取数。
 */
@TableName("att_monthly_closing")
public class MonthlyClosing extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 周期编码（如 2026-01） */
    private String periodCode;

    /** 状态 INIT/RUNNING/DONE/FAILED */
    private String status;

    /** 月结完成时间（UTC） */
    private LocalDateTime closedAt;

    public String getPeriodCode() {
        return periodCode;
    }

    public void setPeriodCode(String periodCode) {
        this.periodCode = periodCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }
}

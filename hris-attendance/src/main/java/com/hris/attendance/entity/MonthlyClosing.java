package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 考勤月结批次（att_monthly_closing）。锁账后本期结果不可逆。
 *
 * <p>状态流转：INIT → RUNNING → DONE / FAILED（docs/03-03 ATT-F-023）。
 * 前置校验：周期内未决异常单必须为 0；锁账后差异只能走下期调整项（ATT-F-024）。</p>
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

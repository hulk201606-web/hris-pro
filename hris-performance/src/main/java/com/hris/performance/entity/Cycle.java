package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.time.LocalDate;

/**
 * 绩效周期（域：绩效，分层：实体/DO）。状态机 DRAFT→ACTIVE→SCORING→CLOSED，
 * 白名单与守卫见 {@link com.hris.performance.service.CycleStateMachine}。
 */
@TableName("prf_cycle")
public class Cycle extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long planId;  // 方案ID(逻辑外键 prf_plan.id)

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }
    private String periodCode;  // 周期编码(如 2026H1)

    public String getPeriodCode() {
        return periodCode;
    }

    public void setPeriodCode(String periodCode) {
        this.periodCode = periodCode;
    }
    private LocalDate startDate;  // 周期开始日

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }
    private LocalDate endDate;  // 周期结束日

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
    private String status;  // 状态 DRAFT/ACTIVE/SCORING/CLOSED

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 法人成本物化快照（cmp_cost_snapshot，批 12 #29）。
 *
 * <p>按 {@code (tenant_id, period_code, legal_entity_id)} 一行，存该期该法人的成本汇总，
 * 供报表 {@code source=snapshot} 走聚合只读库读取（避免每次都扫算薪明细）。</p>
 *
 * <p>{@code legal_entity_id=0} 表示法人未知（emp/org 简报不可用时的降级聚合行）——
 * 用哨兵值而非 NULL 是为了让唯一键在「同一期多条未知法人聚合」时仍然生效。</p>
 */
@TableName("cmp_cost_snapshot")
public class CostSnapshot extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 法人未知哨兵值。 */
    public static final long UNKNOWN_ENTITY = 0L;

    /** 周期编码（如 2026-01） */
    private String periodCode;

    /** 法人 ID（0=未知） */
    private Long legalEntityId;

    /** 人数（按员工去重） */
    private Integer headcount;

    /** 应发合计 */
    private BigDecimal grossTotal;

    /** 扣减合计（应发-实发） */
    private BigDecimal deductionTotal;

    /** 个税合计（MVP 无数据源，恒为 NULL） */
    private BigDecimal taxTotal;

    /** 实发合计 */
    private BigDecimal netTotal;

    /** 快照刷新时间（UTC） */
    private LocalDateTime refreshedAt;

    public String getPeriodCode() {
        return periodCode;
    }

    public void setPeriodCode(String periodCode) {
        this.periodCode = periodCode;
    }

    public Long getLegalEntityId() {
        return legalEntityId;
    }

    public void setLegalEntityId(Long legalEntityId) {
        this.legalEntityId = legalEntityId;
    }

    public Integer getHeadcount() {
        return headcount;
    }

    public void setHeadcount(Integer headcount) {
        this.headcount = headcount;
    }

    public BigDecimal getGrossTotal() {
        return grossTotal;
    }

    public void setGrossTotal(BigDecimal grossTotal) {
        this.grossTotal = grossTotal;
    }

    public BigDecimal getDeductionTotal() {
        return deductionTotal;
    }

    public void setDeductionTotal(BigDecimal deductionTotal) {
        this.deductionTotal = deductionTotal;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public void setTaxTotal(BigDecimal taxTotal) {
        this.taxTotal = taxTotal;
    }

    public BigDecimal getNetTotal() {
        return netTotal;
    }

    public void setNetTotal(BigDecimal netTotal) {
        this.netTotal = netTotal;
    }

    public LocalDateTime getRefreshedAt() {
        return refreshedAt;
    }

    public void setRefreshedAt(LocalDateTime refreshedAt) {
        this.refreshedAt = refreshedAt;
    }
}

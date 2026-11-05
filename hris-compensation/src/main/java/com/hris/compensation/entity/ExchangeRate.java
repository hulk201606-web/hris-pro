package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 汇率（cmp_exchange_rate，CMP-F-023 多币种底座）。
 *
 * <p>按 uk(tenant_id, from_currency, to_currency, effective_date) 唯一；
 * 查询口径取 effective_date ≤ on 的最新一条。</p>
 */
@TableName("cmp_exchange_rate")
public class ExchangeRate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 源币种（ISO 4217 三位字符，如 CNY） */
    private String fromCurrency;

    /** 目标币种（ISO 4217 三位字符，如 USD） */
    private String toCurrency;

    /** 汇率：1 from_currency = rate to_currency */
    private BigDecimal rate;

    /** 生效日期 */
    private LocalDate effectiveDate;

    public String getFromCurrency() {
        return fromCurrency;
    }

    public void setFromCurrency(String fromCurrency) {
        this.fromCurrency = fromCurrency;
    }

    public String getToCurrency() {
        return toCurrency;
    }

    public void setToCurrency(String toCurrency) {
        this.toCurrency = toCurrency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
}

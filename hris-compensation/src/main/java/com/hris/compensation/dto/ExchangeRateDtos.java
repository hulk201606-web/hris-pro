package com.hris.compensation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 汇率 DTO（CMP-F-023）。
 */
public final class ExchangeRateDtos {

    private ExchangeRateDtos() {
    }

    /** 汇率 upsert 请求（同 uk(tenant,from,to,effectiveDate) 存在则更新 rate） */
    public record ExchangeRateUpsertRequest(
            @NotBlank(message = "fromCurrency 不能为空")
            @Size(min = 3, max = 3, message = "fromCurrency 必须为 3 位 ISO 币种码") String fromCurrency,
            @NotBlank(message = "toCurrency 不能为空")
            @Size(min = 3, max = 3, message = "toCurrency 必须为 3 位 ISO 币种码") String toCurrency,
            @NotNull(message = "rate 不能为空")
            @DecimalMin(value = "0", inclusive = false, message = "rate 必须大于 0") BigDecimal rate,
            @NotNull(message = "effectiveDate 不能为空")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate) {
    }

    /** 汇率响应 */
    public record ExchangeRateResponse(
            Long id,
            String fromCurrency,
            String toCurrency,
            BigDecimal rate,
            LocalDate effectiveDate) {
    }
}

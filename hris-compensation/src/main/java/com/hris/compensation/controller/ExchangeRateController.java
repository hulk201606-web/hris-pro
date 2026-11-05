package com.hris.compensation.controller;

import com.hris.common.api.R;
import com.hris.common.dto.PageResult;
import com.hris.common.security.RequirePerm;
import com.hris.compensation.dto.ExchangeRateDtos.ExchangeRateResponse;
import com.hris.compensation.dto.ExchangeRateDtos.ExchangeRateUpsertRequest;
import com.hris.compensation.service.ExchangeRateService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 汇率端点（CMP-F-023）。
 */
@RestController
@RequestMapping("/api/v1/cmp/exchange-rates")
public class ExchangeRateController {

    private final ExchangeRateService rateService;

    public ExchangeRateController(ExchangeRateService rateService) {
        this.rateService = rateService;
    }

    /** 最新生效汇率：effective_date ≤ on（缺省今天）的最新一条；无则 10001「无生效汇率」。 */
    @RequirePerm("cmp:exchange-rate:read")
    @GetMapping
    public R<ExchangeRateResponse> latest(
            @RequestParam("fromCurrency") String fromCurrency,
            @RequestParam("toCurrency") String toCurrency,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        return R.ok(rateService.queryLatest(fromCurrency, toCurrency, on));
    }

    /** 汇率列表（可选分页，缺省第 1 页 20 条），按生效日期倒序。 */
    @RequirePerm("cmp:exchange-rate:read")
    @GetMapping("/list")
    public R<PageResult<ExchangeRateResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(rateService.list(page, size));
    }

    /** upsert：同 uk(tenant, from, to, effectiveDate) 存在则更新 rate。 */
    @RequirePerm("cmp:exchange-rate:write")
    @PostMapping
    public R<ExchangeRateResponse> upsert(@Valid @RequestBody ExchangeRateUpsertRequest req) {
        return R.ok(rateService.upsert(req));
    }
}

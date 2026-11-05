package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.compensation.dto.ExchangeRateDtos.ExchangeRateResponse;
import com.hris.compensation.dto.ExchangeRateDtos.ExchangeRateUpsertRequest;
import com.hris.compensation.entity.ExchangeRate;
import com.hris.compensation.mapper.ExchangeRateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 汇率服务（CMP-F-023 最小可用）：最新生效查询、upsert、分页列表。
 *
 * <p>查询口径：取 effective_date ≤ on 的最新一条（on 缺省今天）；无则 PARAM_INVALID
 * 「无生效汇率」。多租户由 MyBatis-Plus 租户插件隔离。</p>
 */
@Service
public class ExchangeRateService {

    private final ExchangeRateMapper rateMapper;

    public ExchangeRateService(ExchangeRateMapper rateMapper) {
        this.rateMapper = rateMapper;
    }

    /** 最新生效汇率：effective_date ≤ on（缺省今天）的最新一条；无则拒绝。 */
    public ExchangeRateResponse queryLatest(String fromCurrency, String toCurrency, LocalDate on) {
        String from = normalizeCurrency(fromCurrency, "fromCurrency");
        String to = normalizeCurrency(toCurrency, "toCurrency");
        LocalDate day = on == null ? LocalDate.now() : on;
        List<ExchangeRate> rows = rateMapper.selectList(new LambdaQueryWrapper<ExchangeRate>()
                .eq(ExchangeRate::getFromCurrency, from)
                .eq(ExchangeRate::getToCurrency, to)
                .le(ExchangeRate::getEffectiveDate, day)
                .orderByDesc(ExchangeRate::getEffectiveDate)
                .last("limit 1"));
        if (rows == null || rows.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "无生效汇率: " + from + " -> " + to + " on " + day);
        }
        return toResponse(rows.get(0));
    }

    /** upsert：同 uk(tenant, from, to, effective_date) 存在则更新 rate，否则新增。 */
    @Transactional(rollbackFor = Exception.class)
    public ExchangeRateResponse upsert(ExchangeRateUpsertRequest req) {
        String from = normalizeCurrency(req.fromCurrency(), "fromCurrency");
        String to = normalizeCurrency(req.toCurrency(), "toCurrency");
        if (from.equals(to)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "源币种与目标币种不能相同");
        }
        ExchangeRate existing = rateMapper.selectOne(new LambdaQueryWrapper<ExchangeRate>()
                .eq(ExchangeRate::getFromCurrency, from)
                .eq(ExchangeRate::getToCurrency, to)
                .eq(ExchangeRate::getEffectiveDate, req.effectiveDate()));
        ExchangeRate r = existing == null ? new ExchangeRate() : existing;
        r.setFromCurrency(from);
        r.setToCurrency(to);
        r.setRate(req.rate());
        r.setEffectiveDate(req.effectiveDate());
        if (existing == null) {
            rateMapper.insert(r);
        } else {
            r.setRate(req.rate());
            if (rateMapper.updateById(r) == 0) {
                throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
            }
        }
        return toResponse(r);
    }

    /** 分页列表（可选分页，缺省第 1 页 20 条），按生效日期倒序。 */
    public PageResult<ExchangeRateResponse> list(long page, long size) {
        IPage<ExchangeRate> pg = rateMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ExchangeRate>()
                        .orderByDesc(ExchangeRate::getEffectiveDate));
        List<ExchangeRateResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    private String normalizeCurrency(String code, String field) {
        String c = code == null ? "" : code.trim().toUpperCase();
        if (c.length() != 3 || !c.chars().allMatch(Character::isLetter)) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 必须为 3 位 ISO 币种码");
        }
        return c;
    }

    private ExchangeRateResponse toResponse(ExchangeRate r) {
        return new ExchangeRateResponse(r.getId(), r.getFromCurrency(), r.getToCurrency(),
                r.getRate(), r.getEffectiveDate());
    }
}

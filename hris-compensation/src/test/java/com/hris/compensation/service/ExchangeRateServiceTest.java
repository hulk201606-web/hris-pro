package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.compensation.dto.ExchangeRateDtos.ExchangeRateResponse;
import com.hris.compensation.dto.ExchangeRateDtos.ExchangeRateUpsertRequest;
import com.hris.compensation.entity.ExchangeRate;
import com.hris.compensation.mapper.ExchangeRateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 汇率服务单元测试（CMP-F-023）：最新生效选取、无汇率拒绝、upsert、币种码守卫。
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    private static final LocalDate D1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 6, 1);

    @Mock
    private ExchangeRateMapper rateMapper;

    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeRateService(rateMapper);
    }

    private ExchangeRate rate(String from, String to, String rate, LocalDate eff) {
        ExchangeRate r = new ExchangeRate();
        r.setId(1L);
        r.setFromCurrency(from);
        r.setToCurrency(to);
        r.setRate(new BigDecimal(rate));
        r.setEffectiveDate(eff);
        return r;
    }

    @Test
    @DisplayName("最新生效选取：effective_date ≤ on 的多条取最新一条")
    void queryLatestShouldPickLatestEffective() {
        when(rateMapper.selectList(any())).thenReturn(
                List.of(rate("USD", "CNY", "7.2", D2), rate("USD", "CNY", "6.9", D1)));

        ExchangeRateResponse resp = service.queryLatest("USD", "CNY", LocalDate.of(2026, 11, 5));

        assertEquals(D2, resp.effectiveDate());
        assertEquals(new BigDecimal("7.2"), resp.rate());
    }

    @Test
    @DisplayName("无生效汇率 → PARAM_INVALID「无生效汇率」")
    void queryLatestShouldRejectWhenMissing() {
        when(rateMapper.selectList(any())).thenReturn(List.of());

        BizException e = assertThrows(BizException.class,
                () -> service.queryLatest("USD", "CNY", LocalDate.of(2026, 11, 5)));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        assertEquals(true, e.getMessage().contains("无生效汇率"));
    }

    @Test
    @DisplayName("upsert：uk 已存在则更新该行 rate，不新增")
    void upsertShouldUpdateExisting() {
        ExchangeRate existing = rate("USD", "CNY", "6.9", D1);
        when(rateMapper.selectOne(any())).thenReturn(existing);
        when(rateMapper.updateById(existing)).thenReturn(1);

        ExchangeRateResponse resp = service.upsert(
                new ExchangeRateUpsertRequest("usd", "cny", new BigDecimal("7.25"), D1));

        assertEquals("USD", resp.fromCurrency());
        assertEquals(new BigDecimal("7.25"), resp.rate());
        verify(rateMapper).updateById(existing);
        verify(rateMapper, never()).insert(any(ExchangeRate.class));
    }

    @Test
    @DisplayName("upsert：uk 不存在则新增")
    void upsertShouldInsertWhenAbsent() {
        when(rateMapper.selectOne(any())).thenReturn(null);

        service.upsert(new ExchangeRateUpsertRequest("USD", "CNY", new BigDecimal("7.25"), D1));

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(rateMapper).insert(captor.capture());
        assertEquals("USD", captor.getValue().getFromCurrency());
        assertEquals("CNY", captor.getValue().getToCurrency());
        assertEquals(D1, captor.getValue().getEffectiveDate());
    }

    @Test
    @DisplayName("upsert 守卫：from=to 或币种码非 3 位 → PARAM_INVALID")
    void upsertShouldValidateCurrencies() {
        BizException e1 = assertThrows(BizException.class, () -> service.upsert(
                new ExchangeRateUpsertRequest("CNY", "CNY", new BigDecimal("1"), D1)));
        BizException e2 = assertThrows(BizException.class, () -> service.upsert(
                new ExchangeRateUpsertRequest("US", "CNY", new BigDecimal("7"), D1)));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e1.getCode());
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e2.getCode());
    }

    @Test
    @DisplayName("查询守卫：queryLatest 同样校验币种码")
    void queryLatestShouldValidateCurrencies() {
        BizException e = assertThrows(BizException.class,
                () -> service.queryLatest("USDD", "CNY", LocalDate.of(2026, 11, 5)));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
    }

    @Test
    @DisplayName("list：分页透传并转响应")
    @SuppressWarnings("unchecked")
    void listShouldPage() {
        com.baomidou.mybatisplus.core.metadata.IPage<ExchangeRate> pg =
                org.mockito.Mockito.mock(com.baomidou.mybatisplus.core.metadata.IPage.class);
        org.mockito.Mockito.when(pg.getRecords()).thenReturn(List.of(rate("USD", "CNY", "7.2", D2)));
        org.mockito.Mockito.when(pg.getTotal()).thenReturn(1L);
        when(rateMapper.selectPage(any(), any())).thenReturn(pg);

        com.hris.common.dto.PageResult<ExchangeRateResponse> result = service.list(1, 20);

        assertEquals(1L, result.total());
        assertEquals(1, result.records().size());
        verify(rateMapper).selectPage(any(), any(LambdaQueryWrapper.class));
    }
}

package com.hris.compensation.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.compensation.dto.PayrollAdjustmentDtos.PayrollAdjustmentCreateRequest;
import com.hris.compensation.dto.PayrollAdjustmentDtos.PayrollAdjustmentUpdateRequest;
import com.hris.compensation.entity.PayrollAdjustment;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.PayrollAdjustmentMapper;
import com.hris.compensation.mapper.PayrollRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 关账后调整通道单测（CMP-F-020）：锁定期仅允许下一期间新建、锁定期间调整单不可改金额。
 */
@ExtendWith(MockitoExtension.class)
class PayrollAdjustmentServiceTest {

    private static final Long ADJ_ID = 940001L;
    private static final Long RUN_ID = 940002L;

    @Mock
    private PayrollAdjustmentMapper mapper;
    @Mock
    private PayrollRunMapper runMapper;
    @Mock
    private PeriodLockService periodLockService;

    private PayrollAdjustmentService service;

    @BeforeEach
    void setUp() {
        service = new PayrollAdjustmentService(mapper, runMapper, periodLockService);
    }

    private PayrollRun run(String period) {
        PayrollRun e = new PayrollRun();
        e.setId(RUN_ID);
        e.setPeriodCode(period);
        e.setStatus("LOCK");
        return e;
    }

    private PayrollAdjustmentCreateRequest createReq() {
        return new PayrollAdjustmentCreateRequest(RUN_ID, 1L, "BONUS", new BigDecimal("500"), "补发");
    }

    @Test
    @DisplayName("锁定期指向已关账期间 → PARAM_INVALID，不落库")
    void rejectsCreateOnLockedPeriod() {
        when(runMapper.selectById(RUN_ID)).thenReturn(run("2026-01"));
        doThrow(new BizException(ErrorCode.PARAM_INVALID,
                "期间 2026-01 已关账，仅可对下一期间新建调整单"))
                .when(periodLockService).ensureAdjustmentChannelAllowed("2026-01");

        BizException e = assertThrows(BizException.class, () -> service.create(createReq()));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        verify(mapper, never()).insert(any(PayrollAdjustment.class));
    }

    @Test
    @DisplayName("指向下一期间 → 允许新建调整单")
    void allowsCreateOnNextPeriod() {
        when(runMapper.selectById(RUN_ID)).thenReturn(run("2026-02"));

        service.create(createReq());

        verify(periodLockService).ensureAdjustmentChannelAllowed("2026-02");
        verify(mapper).insert(any(PayrollAdjustment.class));
    }

    @Test
    @DisplayName("锁定期间的调整单改金额 → PARAM_INVALID，不更新")
    void rejectsAmountChangeOnLockedPeriod() {
        PayrollAdjustment existing = new PayrollAdjustment();
        existing.setId(ADJ_ID);
        existing.setRunId(RUN_ID);
        existing.setAmount(new BigDecimal("500"));
        when(mapper.selectById(ADJ_ID)).thenReturn(existing);
        when(runMapper.selectById(RUN_ID)).thenReturn(run("2026-01"));
        when(periodLockService.isPeriodLocked("2026-01")).thenReturn(true);

        BizException e = assertThrows(BizException.class, () -> service.update(ADJ_ID,
                new PayrollAdjustmentUpdateRequest(RUN_ID, 1L, "BONUS", new BigDecimal("800"), "改数")));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        verify(mapper, never()).updateById(any(PayrollAdjustment.class));
    }

    @Test
    @DisplayName("锁定期间金额不变（仅改理由）→ 放行")
    void allowsReasonOnlyUpdateOnLockedPeriod() {
        PayrollAdjustment existing = new PayrollAdjustment();
        existing.setId(ADJ_ID);
        existing.setRunId(RUN_ID);
        existing.setAmount(new BigDecimal("500"));
        when(mapper.selectById(ADJ_ID)).thenReturn(existing);
        when(runMapper.selectById(RUN_ID)).thenReturn(run("2026-01"));
        when(periodLockService.isPeriodLocked("2026-01")).thenReturn(true);
        when(mapper.updateById(any(PayrollAdjustment.class))).thenReturn(1);

        service.update(ADJ_ID,
                new PayrollAdjustmentUpdateRequest(RUN_ID, 1L, "BONUS", new BigDecimal("500.00"), "补充说明"));

        verify(mapper).updateById(any(PayrollAdjustment.class));
    }
}

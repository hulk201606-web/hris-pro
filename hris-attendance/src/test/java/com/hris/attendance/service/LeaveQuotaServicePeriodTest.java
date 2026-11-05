package com.hris.attendance.service;

import com.hris.attendance.dto.LeaveQuotaDtos.QuotaGrantRequest;
import com.hris.attendance.dto.LeaveQuotaDtos.QuotaTransferRequest;
import com.hris.attendance.entity.LeaveLedger;
import com.hris.attendance.entity.LeaveQuota;
import com.hris.attendance.entity.LeaveType;
import com.hris.attendance.mapper.LeaveLedgerMapper;
import com.hris.attendance.mapper.LeaveQuotaMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 额度 period 年度口径校验测试（docs/19 §9-2）：
 * 授予/结转的 period 必须是 4 位年份，与请假侧 String.valueOf(year) 取额度对齐，
 * 否则按月授的额度请假时报「假期额度不足」。
 */
@ExtendWith(MockitoExtension.class)
class LeaveQuotaServicePeriodTest {

    @Mock
    private LeaveQuotaMapper quotaMapper;
    @Mock
    private LeaveLedgerMapper ledgerMapper;
    @Mock
    private LeaveTypeService leaveTypeService;

    private LeaveQuotaService service() {
        return new LeaveQuotaService(quotaMapper, ledgerMapper, leaveTypeService);
    }

    private LeaveType stubType() {
        LeaveType t = new LeaveType();
        t.setId(1L);
        t.setName("年假");
        return t;
    }

    @Test
    @DisplayName("grant 月度串 2026-11 → PARAM_INVALID，拒绝发放")
    void grantShouldRejectMonthlyPeriod() {
        QuotaGrantRequest req = new QuotaGrantRequest(200001L, 1L, "2026-11", new BigDecimal("80.00"));

        BizException e = assertThrows(BizException.class, () -> service().grant(req));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("4 位年份"), "实际: " + e.getMessage());
        verify(quotaMapper, never()).insert(any(LeaveQuota.class));
        verify(ledgerMapper, never()).insert(any(LeaveLedger.class));
    }

    @Test
    @DisplayName("grant 合法年串 2026 → 通过，新额度行 period=2026 并落流水")
    void grantShouldAcceptYearPeriod() {
        when(leaveTypeService.require(1L)).thenReturn(stubType());
        when(quotaMapper.selectOne(any())).thenReturn(null);
        QuotaGrantRequest req = new QuotaGrantRequest(200001L, 1L, "2026", new BigDecimal("80.00"));

        service().grant(req);

        ArgumentCaptor<LeaveQuota> captor = ArgumentCaptor.forClass(LeaveQuota.class);
        verify(quotaMapper).insert(captor.capture());
        assertEquals("2026", captor.getValue().getPeriod());
        assertEquals(new BigDecimal("80.00"), captor.getValue().getTotalQuota());
    }

    @Test
    @DisplayName("transfer 源周期为月度串 → PARAM_INVALID，拒绝结转")
    void transferShouldRejectInvalidFromPeriod() {
        QuotaTransferRequest req = new QuotaTransferRequest(200001L, 1L, "2025-12", "2026", new BigDecimal("3.00"));

        BizException e = assertThrows(BizException.class, () -> service().transfer(req));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        verify(quotaMapper, never()).updateById(any(LeaveQuota.class));
    }

    @Test
    @DisplayName("transfer 目标周期为月度串 → PARAM_INVALID，拒绝结转")
    void transferShouldRejectInvalidToPeriod() {
        QuotaTransferRequest req = new QuotaTransferRequest(200001L, 1L, "2025", "2026-01", new BigDecimal("3.00"));

        BizException e = assertThrows(BizException.class, () -> service().transfer(req));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        verify(quotaMapper, never()).updateById(any(LeaveQuota.class));
    }

    @Test
    @DisplayName("transfer 合法年串（2025→2026）→ 通过，额度从源行转出、目标行新建")
    void transferShouldAcceptYearPeriods() {
        when(leaveTypeService.require(1L)).thenReturn(stubType());
        LeaveQuota from = new LeaveQuota();
        from.setTotalQuota(new BigDecimal("10.00"));
        from.setUsedQuota(new BigDecimal("2.00"));
        when(quotaMapper.selectOne(any())).thenReturn(from, (LeaveQuota) null);
        when(quotaMapper.updateById(any(LeaveQuota.class))).thenReturn(1);
        QuotaTransferRequest req = new QuotaTransferRequest(200001L, 1L, "2025", "2026", new BigDecimal("3.00"));

        service().transfer(req);

        ArgumentCaptor<LeaveQuota> captor = ArgumentCaptor.forClass(LeaveQuota.class);
        verify(quotaMapper).updateById(captor.capture());
        assertEquals(new BigDecimal("7.00"), captor.getValue().getTotalQuota());
        verify(quotaMapper).insert(any(LeaveQuota.class));
    }
}

package com.hris.attendance.service;

import com.hris.attendance.dto.ClosingDtos.AdjustmentCreateRequest;
import com.hris.attendance.dto.ClosingDtos.AdjustmentResponse;
import com.hris.attendance.dto.ClosingDtos.ClosingResponse;
import com.hris.attendance.entity.ClosingAdjustment;
import com.hris.attendance.entity.MonthlyClosing;
import com.hris.attendance.entity.PayrollCycle;
import com.hris.attendance.mapper.ClosingAdjustmentMapper;
import com.hris.attendance.mapper.MonthlyClosingMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 月结调整项与批次列表单测（红冲通道）。
 *
 * <p>月结锁账后历史结果不可改，差异一律通过调整项进入下期——这条通道的
 * 「类型归一化」「批次存在性守卫」「列表实时带出未决异常数」需独立锁定。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MonthlyClosingService：调整项与列表")
class MonthlyClosingAdjustmentTest {

    private static final LocalDate START = LocalDate.parse("2026-10-01");
    private static final LocalDate END = LocalDate.parse("2026-10-31");

    @Mock
    private MonthlyClosingMapper closingMapper;
    @Mock
    private ClosingAdjustmentMapper adjustmentMapper;
    @Mock
    private PayrollCycleService cycleService;
    @Mock
    private ExceptionService exceptionService;
    @Mock
    private DailyAttendanceService dailyService;

    private MonthlyClosingService service;

    @BeforeEach
    void setUp() {
        service = new MonthlyClosingService(closingMapper, adjustmentMapper,
                cycleService, exceptionService, dailyService);
    }

    private MonthlyClosing stubClosing(String status) {
        MonthlyClosing c = new MonthlyClosing();
        c.setId(900001L);
        c.setPeriodCode("2026-10");
        c.setStatus(status);
        return c;
    }

    private PayrollCycle stubCycle() {
        PayrollCycle c = new PayrollCycle();
        c.setPeriodCode("2026-10");
        c.setStartDate(START);
        c.setEndDate(END);
        c.setStatus("OPEN");
        return c;
    }

    // ==================== 调整项（红冲通道） ====================
    @Nested
    @DisplayName("调整项")
    class Adjustments {

        @Test
        @DisplayName("新增调整项：类型转大写、金额 null 兜底为 0")
        void addsAdjustmentNormalizingType() {
            when(closingMapper.selectById(900001L)).thenReturn(stubClosing(
                    MonthlyClosingService.STATUS_DONE));
            when(adjustmentMapper.insert(any(ClosingAdjustment.class))).thenReturn(1);

            AdjustmentResponse r = service.addAdjustment(
                    new AdjustmentCreateRequest(200001L, 900001L, "retro", null, "补发"));

            assertEquals("RETRO", r.adjustType(), "类型须归一化为大写");
            assertEquals(0, BigDecimal.ZERO.compareTo(r.amount()));
            verify(adjustmentMapper).insert(any(ClosingAdjustment.class));
        }

        @Test
        @DisplayName("月结批次不存在 → NOT_FOUND")
        void rejectsUnknownClosing() {
            when(closingMapper.selectById(999999L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.addAdjustment(new AdjustmentCreateRequest(
                            200001L, 999999L, "retro", new BigDecimal("1"), null)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(adjustmentMapper, never()).insert(any(ClosingAdjustment.class));
        }

        @Test
        @DisplayName("按批次查询调整项，closingId 为 null 时查全部")
        void queriesAdjustments() {
            when(adjustmentMapper.selectList(any())).thenReturn(List.of());

            assertTrue(service.adjustments(900001L).isEmpty());
            assertTrue(service.adjustments(null).isEmpty());
        }
    }

    // ==================== list ====================
    @Test
    @DisplayName("list：未完成批次实时带出未决异常数")
    void listExposesPendingForUnfinished() {
        when(closingMapper.selectList(any())).thenReturn(List.of(
                stubClosing(MonthlyClosingService.STATUS_INIT)));
        when(cycleService.requireByPeriod("2026-10")).thenReturn(stubCycle());
        when(exceptionService.countOpen(START, END)).thenReturn(5L);

        List<ClosingResponse> list = service.list();

        assertEquals(1, list.size());
        assertEquals(5L, list.get(0).pendingExceptionCount());
        assertNull(list.get(0).closedAt());
    }

    @Test
    @DisplayName("list：DONE 批次不再重复查询未决异常（省一次 DB 往返）")
    void listSkipsPendingQueryForDone() {
        when(closingMapper.selectList(any())).thenReturn(List.of(
                stubClosing(MonthlyClosingService.STATUS_DONE)));

        List<ClosingResponse> list = service.list();

        assertEquals(0L, list.get(0).pendingExceptionCount());
        verify(exceptionService, never()).countOpen(any(), any());
    }
}
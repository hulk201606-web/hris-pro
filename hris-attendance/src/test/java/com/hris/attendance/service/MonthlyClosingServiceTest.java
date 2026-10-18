package com.hris.attendance.service;

import com.hris.attendance.dto.ClosingDtos.ClosingDetailResponse;
import com.hris.attendance.dto.ClosingDtos.ClosingResponse;
import com.hris.attendance.dto.ClosingDtos.ClosingRunRequest;
import com.hris.attendance.entity.DailyAttendance;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 月结服务单测（docs/16 L1）：锁账状态机 + 幂等守卫 + 结果集口径聚合。
 *
 * <p>月结是考勤域对下游算薪的口径来源，锁账后历史不可改（差异走调整项进下期），
 * 因此状态守卫与聚合口径必须被测试锁定。</p>
 */
@ExtendWith(MockitoExtension.class)
class MonthlyClosingServiceTest {

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

    private PayrollCycle stubCycle() {
        PayrollCycle c = new PayrollCycle();
        c.setPeriodCode("2026-10");
        c.setStartDate(START);
        c.setEndDate(END);
        c.setStatus("OPEN");
        return c;
    }

    private MonthlyClosing stubClosing(String status) {
        MonthlyClosing c = new MonthlyClosing();
        c.setId(900001L);
        c.setPeriodCode("2026-10");
        c.setStatus(status);
        return c;
    }

    private DailyAttendance stubDaily(Long empId, String status,
                                      String overtime, String leave) {
        DailyAttendance d = new DailyAttendance();
        d.setId(null);
        d.setEmpId(empId);
        d.setWorkDate(START);
        d.setAttStatus(status);
        if (overtime != null) {
            d.setOvertimeHours(new BigDecimal(overtime));
        }
        if (leave != null) {
            d.setLeaveHours(new BigDecimal(leave));
        }
        return d;
    }

    // ==================== run：锁账状态机 ====================
    @Nested
    @DisplayName("run 月结锁账")
    class Run {

        @Test
        @DisplayName("首次月结：无未决异常 → 置 DONE 并写入锁账时间")
        void firstRunSucceeds() {
            when(cycleService.requireByPeriod("2026-10")).thenReturn(stubCycle());
            when(closingMapper.selectOne(any())).thenReturn(null);
            when(closingMapper.insert(any(MonthlyClosing.class))).thenReturn(1);
            when(closingMapper.updateById(any(MonthlyClosing.class))).thenReturn(1);
            when(exceptionService.countOpen(START, END)).thenReturn(0L);

            ClosingResponse res = service.run(new ClosingRunRequest("2026-10"));

            assertEquals("2026-10", res.periodCode());
            assertEquals(MonthlyClosingService.STATUS_DONE, res.status());
            assertNotNull(res.closedAt(), "锁账成功后必须写入 closedAt");
            verify(closingMapper).insert(any(MonthlyClosing.class));
        }

        @Test
        @DisplayName("重复锁账幂等：已 DONE 的周期拒再次执行")
        void rejectsRepeatWhenDone() {
            when(cycleService.requireByPeriod("2026-10")).thenReturn(stubCycle());
            when(closingMapper.selectOne(any())).thenReturn(stubClosing(
                    MonthlyClosingService.STATUS_DONE));

            BizException e = assertThrows(BizException.class,
                    () -> service.run(new ClosingRunRequest("2026-10")));

            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
            assertTrue(e.getMessage().contains("不可重复锁账"));
            verify(closingMapper, never()).updateById(any(MonthlyClosing.class));
        }

        @Test
        @DisplayName("未决异常 > 0 → 置 FAILED 并抛错（不锁账）")
        void failsWhenPendingExceptionsExist() {
            when(cycleService.requireByPeriod("2026-10")).thenReturn(stubCycle());
            when(closingMapper.selectOne(any())).thenReturn(null);
            when(closingMapper.insert(any(MonthlyClosing.class))).thenReturn(1);
            when(closingMapper.updateById(any(MonthlyClosing.class))).thenReturn(1);
            when(exceptionService.countOpen(START, END)).thenReturn(3L);

            BizException e = assertThrows(BizException.class,
                    () -> service.run(new ClosingRunRequest("2026-10")));

            assertTrue(e.getMessage().contains("3"), "错误信息须带出未决异常数量");
            assertTrue(e.getMessage().contains("未决考勤异常"));
        }

        @Test
        @DisplayName("重算（FAILED/INIT 状态）允许再次执行")
        void allowsRerunWhenNotDone() {
            when(cycleService.requireByPeriod("2026-10")).thenReturn(stubCycle());
            when(closingMapper.selectOne(any())).thenReturn(stubClosing(
                    MonthlyClosingService.STATUS_FAILED));
            when(closingMapper.updateById(any(MonthlyClosing.class))).thenReturn(1);
            when(exceptionService.countOpen(START, END)).thenReturn(0L);

            ClosingResponse res = service.run(new ClosingRunRequest("2026-10"));

            assertEquals(MonthlyClosingService.STATUS_DONE, res.status());
            verify(closingMapper, never()).insert(any(MonthlyClosing.class));
        }

        @Test
        @DisplayName("并发写冲突：updateById 影响 0 行 → 抛错提示刷新")
        void rejectsOptimisticLockConflict() {
            when(cycleService.requireByPeriod("2026-10")).thenReturn(stubCycle());
            when(closingMapper.selectOne(any())).thenReturn(stubClosing(
                    MonthlyClosingService.STATUS_RUNNING));
            when(closingMapper.updateById(any(MonthlyClosing.class))).thenReturn(0);

            BizException e = assertThrows(BizException.class,
                    () -> service.run(new ClosingRunRequest("2026-10")));

            assertTrue(e.getMessage().contains("刷新"));
        }

        @Test
        @DisplayName("周期编码两端空白被裁剪后再查")
        void trimsPeriodCode() {
            when(cycleService.requireByPeriod("2026-10")).thenReturn(stubCycle());
            when(closingMapper.selectOne(any())).thenReturn(stubClosing(
                    MonthlyClosingService.STATUS_DONE));

            assertThrows(BizException.class,
                    () -> service.run(new ClosingRunRequest("  2026-10  ")));
            verify(cycleService).requireByPeriod("2026-10");
        }
    }
}

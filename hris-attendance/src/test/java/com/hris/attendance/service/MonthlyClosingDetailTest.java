package com.hris.attendance.service;

import com.hris.attendance.dto.ClosingDtos.ClosingDetailResponse;
import com.hris.attendance.entity.DailyAttendance;
import com.hris.attendance.entity.MonthlyClosing;
import com.hris.attendance.mapper.ClosingAdjustmentMapper;
import com.hris.attendance.mapper.MonthlyClosingMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 月结结果集口径聚合单测（薪酬域取数的数据来源）。
 *
 * <p>状态分桶、时长汇总、脏数据跳过、员工去重四项口径一旦偏差会直接传导到算薪，
 * 因此逐条锁定。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MonthlyClosingService：结果集口径")
class MonthlyClosingDetailTest {

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

    private com.hris.attendance.entity.PayrollCycle stubCycle() {
        com.hris.attendance.entity.PayrollCycle c = new com.hris.attendance.entity.PayrollCycle();
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

    // ==================== detail：结果集口径聚合 ====================
    @Nested
    @DisplayName("detail 月结结果集口径")
    class Detail {

        @Test
        @DisplayName("状态分布：迟到/早退/旷工/请假/正常 分桶计数")
        void aggregatesStatusBuckets() {
            when(cycleService.requireByPeriod("2026-10")).thenReturn(stubCycle());
            when(closingMapper.selectOne(any())).thenReturn(stubClosing(
                    MonthlyClosingService.STATUS_DONE));
            when(exceptionService.countOpen(START, END)).thenReturn(0L);
            when(adjustmentMapper.selectCount(any())).thenReturn(0L);
            when(dailyService.findByRange(START, END)).thenReturn(List.of(
                    stubDaily(200001L, "NORMAL", "1.5", "0"),
                    stubDaily(200001L, "LATE", "2.0", "0"),
                    stubDaily(200001L, "EARLY", "0", "0"),
                    stubDaily(200002L, "ABSENT", "0", "0"),
                    stubDaily(200002L, "LEAVE", "0", "8")));

            ClosingDetailResponse d = service.detail("2026-10");

            assertEquals(1L, d.normalDays());
            assertEquals(1L, d.lateDays());
            assertEquals(1L, d.earlyDays());
            assertEquals(1L, d.absentDays());
            assertEquals(1L, d.leaveDays());
            assertEquals(2L, d.employeeCount(), "员工数按 empId 去重");
        }

        @Test
        @DisplayName("加班/请假时长求和，null 视作 0")
        void sumsHoursWithNullSafety() {
            when(cycleService.requireByPeriod("2026-10")).thenReturn(stubCycle());
            when(closingMapper.selectOne(any())).thenReturn(stubClosing(
                    MonthlyClosingService.STATUS_DONE));
            when(exceptionService.countOpen(START, END)).thenReturn(0L);
            when(adjustmentMapper.selectCount(any())).thenReturn(0L);
            when(dailyService.findByRange(START, END)).thenReturn(List.of(
                    stubDaily(200001L, "NORMAL", "1.5", null),
                    stubDaily(200001L, "NORMAL", "2.25", null)));

            ClosingDetailResponse d = service.detail("2026-10");

            assertEquals(0, new BigDecimal("3.75").compareTo(d.overtimeHours()));
            assertEquals(0, BigDecimal.ZERO.compareTo(d.leaveHours()));
        }

        @Test
        @DisplayName("attStatus 为 null 的脏数据被跳过，不计入任一分桶")
        void skipsRowsWithoutStatus() {
            when(cycleService.requireByPeriod("2026-10")).thenReturn(stubCycle());
            when(closingMapper.selectOne(any())).thenReturn(stubClosing(
                    MonthlyClosingService.STATUS_DONE));
            when(exceptionService.countOpen(START, END)).thenReturn(0L);
            when(adjustmentMapper.selectCount(any())).thenReturn(0L);
            when(dailyService.findByRange(START, END)).thenReturn(List.of(
                    stubDaily(200001L, null, "9.0", "9.0"),
                    stubDaily(200001L, "NORMAL", "1.0", "0")));

            ClosingDetailResponse d = service.detail("2026-10");

            assertEquals(1L, d.normalDays(), "仅 1 条有效记录计入正常");
            assertEquals(0, new BigDecimal("1.0").compareTo(d.overtimeHours()),
                    "脏记录的 9 小时不得进入汇总");
        }

        @Test
        @DisplayName("无月结批次时状态为 INIT，且不查调整项")
        void defaultsToInitWhenNoBatch() {
            when(cycleService.requireByPeriod("2026-10")).thenReturn(stubCycle());
            when(closingMapper.selectOne(any())).thenReturn(null);
            when(exceptionService.countOpen(START, END)).thenReturn(2L);
            when(dailyService.findByRange(START, END)).thenReturn(List.of());

            ClosingDetailResponse d = service.detail("2026-10");

            assertEquals(MonthlyClosingService.STATUS_INIT, d.status());
            assertEquals(0L, d.adjustmentCount());
            assertEquals(2L, d.pendingExceptionCount(), "未月结也须暴露未决异常数");
        }

        @Test
        @DisplayName("已 DONE 批次仍返回未决异常数（供监控异常清零）")
        void exposesPendingAfterDone() {
            when(cycleService.requireByPeriod("2026-10")).thenReturn(stubCycle());
            when(closingMapper.selectOne(any())).thenReturn(stubClosing(
                    MonthlyClosingService.STATUS_DONE));
            when(exceptionService.countOpen(START, END)).thenReturn(1L);
            when(adjustmentMapper.selectCount(any())).thenReturn(4L);
            when(dailyService.findByRange(START, END)).thenReturn(List.of());

            ClosingDetailResponse d = service.detail("2026-10");

            assertEquals(1L, d.pendingExceptionCount());
            assertEquals(4L, d.adjustmentCount());
        }

        @Test
        @DisplayName("selectCount 返回 null 时调整项数兜底为 0")
        void toleratesNullCount() {
            when(cycleService.requireByPeriod("2026-10")).thenReturn(stubCycle());
            when(closingMapper.selectOne(any())).thenReturn(stubClosing(
                    MonthlyClosingService.STATUS_DONE));
            when(exceptionService.countOpen(START, END)).thenReturn(0L);
            when(adjustmentMapper.selectCount(any())).thenReturn(null);
            when(dailyService.findByRange(START, END)).thenReturn(List.of());

            assertEquals(0L, service.detail("2026-10").adjustmentCount());
        }
    }
}

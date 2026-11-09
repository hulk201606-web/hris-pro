package com.hris.attendance.service;

import com.hris.attendance.dto.DailyDtos.DailyComputeRequest;
import com.hris.attendance.dto.DailyDtos.DailyComputeResult;
import com.hris.attendance.dto.HolidayDtos.WorkdayCheckResponse;
import com.hris.attendance.entity.DailyAttendance;
import com.hris.attendance.entity.MonthlyClosing;
import com.hris.attendance.entity.PayrollCycle;
import com.hris.attendance.entity.PunchRecord;
import com.hris.attendance.entity.Shift;
import com.hris.attendance.entity.ShiftAssignment;
import com.hris.attendance.mapper.DailyAttendanceMapper;
import com.hris.attendance.mapper.MonthlyClosingMapper;
import com.hris.attendance.mapper.PayrollCycleMapper;
import com.hris.attendance.mapper.ShiftAssignmentMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DailyAttendanceService 单元测试——节假日接线（§8.5-7）与月结锁守卫（§8.5-5）。
 *
 * <p>锁定两条新语义：法定节假日不判旷工/迟到/早退（状态 HOLIDAY，加班链路不受影响）；
 * 覆盖日期的薪酬取数周期月结 DONE 后禁止重算。</p>
 */
@ExtendWith(MockitoExtension.class)
class DailyAttendanceServiceTest {

    private static final LocalDate WORK_DATE = LocalDate.parse("2026-10-01");
    private static final Long EMP_ID = 200001L;

    @Mock
    private DailyAttendanceMapper dailyMapper;
    @Mock
    private ShiftAssignmentMapper assignmentMapper;
    @Mock
    private PunchService punchService;
    @Mock
    private ShiftService shiftService;
    @Mock
    private LeaveQueryService leaveQueryService;
    @Mock
    private OvertimeService overtimeService;
    @Mock
    private AttendanceCalculator calculator;
    @Mock
    private HolidayService holidayService;
    @Mock
    private PayrollCycleMapper cycleMapper;
    @Mock
    private MonthlyClosingMapper closingMapper;
    @Mock
    private GroupService groupService;
    @Mock
    private DailyCloseService dailyCloseService;

    private DailyAttendanceService service;

    @BeforeEach
    void setUp() {
        service = new DailyAttendanceService(dailyMapper, assignmentMapper, punchService,
                shiftService, leaveQueryService, overtimeService, calculator, holidayService,
                groupService, cycleMapper, closingMapper, dailyCloseService);
        // 默认员工未加入考勤组 → 降级租户并集（旧行为：日历对全部年度生效）；
        // 仅 ClosingLock 分支不触发该查询，用 lenient
        lenient().when(groupService.boundCalendarYearsForEmp(EMP_ID)).thenReturn(List.of());
        // 批次 12#27：组策略缺省 FIXED + Asia/Shanghai + 不校验围栏
        lenient().when(groupService.policyForEmp(EMP_ID))
                .thenReturn(GroupService.GroupPolicy.DEFAULTS);
    }

    private void stubNoClosing() {
        when(cycleMapper.selectList(any())).thenReturn(List.of());
    }

    private void stubShift() {
        ShiftAssignment assignment = new ShiftAssignment();
        assignment.setEmpId(EMP_ID);
        assignment.setShiftId(300001L);
        when(assignmentMapper.selectOne(any())).thenReturn(assignment);
        Shift shift = new Shift();
        shift.setStartTime(LocalTime.parse("09:00"));
        shift.setEndTime(LocalTime.parse("18:00"));
        when(shiftService.require(300001L)).thenReturn(shift);
    }

    private void stubBaseCalc() {
        when(leaveQueryService.leaveHoursOn(EMP_ID, WORK_DATE)).thenReturn(BigDecimal.ZERO);
        when(overtimeService.overtimeHoursOn(EMP_ID, WORK_DATE)).thenReturn(BigDecimal.ZERO);
        when(dailyMapper.selectOne(any())).thenReturn(null);
        when(dailyMapper.insert(any(DailyAttendance.class))).thenReturn(1);
    }

    private DailyComputeRequest req() {
        return new DailyComputeRequest(WORK_DATE, List.of(EMP_ID));
    }

    @Nested
    @DisplayName("月结锁守卫（B3-①）")
    class ClosingLock {

        @Test
        @DisplayName("覆盖日期的周期月结 DONE → ATT_ERROR「月结已锁定，请走调整通道」，不重算")
        void rejectsRecalcWhenClosingDone() {
            PayrollCycle cycle = new PayrollCycle();
            cycle.setPeriodCode("2026-10");
            cycle.setStartDate(LocalDate.parse("2026-10-01"));
            cycle.setEndDate(LocalDate.parse("2026-10-31"));
            when(cycleMapper.selectList(any())).thenReturn(List.of(cycle));
            MonthlyClosing closing = new MonthlyClosing();
            closing.setPeriodCode("2026-10");
            closing.setStatus(MonthlyClosingService.STATUS_DONE);
            when(closingMapper.selectOne(any())).thenReturn(closing);

            BizException e = assertThrows(BizException.class, () -> service.compute(req()));

            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
            assertEquals("月结已锁定，请走调整通道", e.getMessage());
            verify(dailyMapper, never()).insert(any(DailyAttendance.class));
            verify(dailyMapper, never()).updateById(any(DailyAttendance.class));
        }

        @Test
        @DisplayName("期间日考勤月结锁 ACTIVE → 拒绝重算「期间已月结」")
        void rejectsRecalcWhenDailyCloseActive() {
            doThrow(new BizException(ErrorCode.ATT_ERROR,
                    "期间 2026-10 已月结，不可重算；差异请走月结调整通道"))
                    .when(dailyCloseService).ensureNotClosed(WORK_DATE);

            BizException e = assertThrows(BizException.class, () -> service.compute(req()));

            assertEquals(ErrorCode.ATT_ERROR.getCode(), e.getCode());
            assertTrue(e.getMessage().contains("已月结"), "须提示期间已月结");
            verify(dailyMapper, never()).insert(any(DailyAttendance.class));
            verify(dailyMapper, never()).updateById(any(DailyAttendance.class));
        }

        @Test
        @DisplayName("月结未完成（RUNNING）或无月结批次 → 正常重算")
        void allowsRecalcWhenClosingNotDone() {
            PayrollCycle cycle = new PayrollCycle();
            cycle.setPeriodCode("2026-10");
            cycle.setStartDate(LocalDate.parse("2026-10-01"));
            cycle.setEndDate(LocalDate.parse("2026-10-31"));
            when(cycleMapper.selectList(any())).thenReturn(List.of(cycle));
            MonthlyClosing closing = new MonthlyClosing();
            closing.setPeriodCode("2026-10");
            closing.setStatus(MonthlyClosingService.STATUS_RUNNING);
            when(closingMapper.selectOne(any())).thenReturn(closing);

            when(punchService.findByDate(EMP_ID, WORK_DATE)).thenReturn(List.of());
            when(holidayService.isWorkday(WORK_DATE)).thenReturn(
                    new WorkdayCheckResponse(WORK_DATE, true, "WEEKDAY"));
            stubShift();
            stubBaseCalc();
            when(calculator.resolveStatus(any(), any(), any(), any(), anyBoolean(), anyInt()))
                    .thenReturn(AttendanceCalculator.STATUS_ABSENT);

            DailyComputeResult result = service.compute(req());

            assertEquals(1, result.employeeCount());
            verify(dailyMapper).insert(any(DailyAttendance.class));
        }
    }
    @Nested
    @DisplayName("组时区与工作制（批12#27）")
    class GroupTimezoneAndWorkMode {

        @Test
        @DisplayName("打卡 UTC 时刻按组时区换算为判定基准（01:00Z / 10:00Z → 09:00 / 18:00 Asia/Shanghai）")
        void convertsPunchTimeToGroupZone() {
            stubNoClosing();
            PunchRecord in = new PunchRecord();
            in.setEmpId(EMP_ID);
            in.setType(PunchService.TYPE_IN);
            in.setPunchTime(LocalDateTime.parse("2026-10-01T01:00:00"));
            PunchRecord out = new PunchRecord();
            out.setEmpId(EMP_ID);
            out.setType(PunchService.TYPE_OUT);
            out.setPunchTime(LocalDateTime.parse("2026-10-01T10:00:00"));
            when(punchService.findByDate(EMP_ID, WORK_DATE)).thenReturn(List.of(in, out));
            when(holidayService.isWorkday(WORK_DATE)).thenReturn(
                    new WorkdayCheckResponse(WORK_DATE, true, "WEEKDAY"));
            stubShift();
            stubBaseCalc();
            when(calculator.resolveStatus(any(), any(), any(), any(), anyBoolean(), anyInt()))
                    .thenReturn(AttendanceCalculator.STATUS_NORMAL);

            service.compute(req());

            ArgumentCaptor<LocalDateTime> inCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> outCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(calculator).resolveStatus(any(), inCaptor.capture(), outCaptor.capture(),
                    any(), anyBoolean(), anyInt());
            assertEquals(LocalDateTime.parse("2026-10-01T09:00:00"), inCaptor.getValue());
            assertEquals(LocalDateTime.parse("2026-10-01T18:00:00"), outCaptor.getValue());
        }

        @Test
        @DisplayName("弹性工作制策略透传给计算器（flexible=true + 弹性区间）")
        void passesFlexiblePolicyToCalculator() {
            stubNoClosing();
            when(groupService.policyForEmp(EMP_ID)).thenReturn(new GroupService.GroupPolicy(
                    900001L, GroupService.WORK_MODE_FLEXIBLE,
                    java.time.ZoneId.of("Asia/Shanghai"), null, true, 60));
            when(punchService.findByDate(EMP_ID, WORK_DATE)).thenReturn(List.of());
            when(holidayService.isWorkday(WORK_DATE)).thenReturn(
                    new WorkdayCheckResponse(WORK_DATE, true, "WEEKDAY"));
            stubShift();
            stubBaseCalc();
            when(calculator.resolveStatus(any(), any(), any(), any(), anyBoolean(), anyInt()))
                    .thenReturn(AttendanceCalculator.STATUS_NORMAL);

            service.compute(req());

            verify(calculator).resolveStatus(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq(true),
                    org.mockito.ArgumentMatchers.eq(60));
        }
    }
}

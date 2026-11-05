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
import static org.mockito.ArgumentMatchers.any;
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

    private DailyAttendanceService service;

    @BeforeEach
    void setUp() {
        service = new DailyAttendanceService(dailyMapper, assignmentMapper, punchService,
                shiftService, leaveQueryService, overtimeService, calculator, holidayService,
                cycleMapper, closingMapper);
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
            when(calculator.resolveStatus(any(), any(), any(), any()))
                    .thenReturn(AttendanceCalculator.STATUS_ABSENT);

            DailyComputeResult result = service.compute(req());

            assertEquals(1, result.employeeCount());
            verify(dailyMapper).insert(any(DailyAttendance.class));
        }
    }

    @Nested
    @DisplayName("法定节假日语义（任务 A）")
    class LegalHoliday {

        @Test
        @DisplayName("法定节假日 + 有排班 + 无打卡 → HOLIDAY，不判旷工，不生成异常")
        void holidayWithoutPunchIsNotAbsent() {
            stubNoClosing();
            when(punchService.findByDate(EMP_ID, WORK_DATE)).thenReturn(List.of());
            when(holidayService.isWorkday(WORK_DATE)).thenReturn(
                    new WorkdayCheckResponse(WORK_DATE, false, "HOLIDAY"));
            stubShift();
            stubBaseCalc();

            DailyComputeResult result = service.compute(req());

            assertEquals(1, result.employeeCount());
            assertEquals(0, result.absentCount(), "节假日不得计入旷工");
            ArgumentCaptor<DailyAttendance> captor = ArgumentCaptor.forClass(DailyAttendance.class);
            verify(dailyMapper).insert(captor.capture());
            assertEquals(AttendanceCalculator.STATUS_HOLIDAY, captor.getValue().getAttStatus());
            verify(calculator, never()).resolveStatus(any(), any(), any(), any());
            verify(calculator, never()).raiseExceptions(any(), any());
        }

        @Test
        @DisplayName("法定节假日 + 无排班无打卡 → 仍落 HOLIDAY 记录（不按休息日跳过）")
        void holidayWithoutShiftStillRecorded() {
            stubNoClosing();
            when(punchService.findByDate(EMP_ID, WORK_DATE)).thenReturn(List.of());
            when(holidayService.isWorkday(WORK_DATE)).thenReturn(
                    new WorkdayCheckResponse(WORK_DATE, false, "HOLIDAY"));
            when(assignmentMapper.selectOne(any())).thenReturn(null);
            stubBaseCalc();

            service.compute(req());

            ArgumentCaptor<DailyAttendance> captor = ArgumentCaptor.forClass(DailyAttendance.class);
            verify(dailyMapper).insert(captor.capture());
            assertEquals(AttendanceCalculator.STATUS_HOLIDAY, captor.getValue().getAttStatus());
        }

        @Test
        @DisplayName("法定节假日 + 有打卡 → 状态仍 HOLIDAY，既有加班时长照常落库")
        void holidayWithPunchKeepsOvertimeHours() {
            stubNoClosing();
            PunchRecord punchIn = new PunchRecord();
            punchIn.setEmpId(EMP_ID);
            punchIn.setType(PunchService.TYPE_IN);
            punchIn.setPunchTime(LocalDateTime.parse("2026-10-01T09:00:00"));
            when(punchService.findByDate(EMP_ID, WORK_DATE))
                    .thenReturn(List.of(punchIn));
            when(holidayService.isWorkday(WORK_DATE)).thenReturn(
                    new WorkdayCheckResponse(WORK_DATE, false, "HOLIDAY"));
            stubShift();
            when(leaveQueryService.leaveHoursOn(EMP_ID, WORK_DATE)).thenReturn(BigDecimal.ZERO);
            when(overtimeService.overtimeHoursOn(EMP_ID, WORK_DATE))
                    .thenReturn(new BigDecimal("2.00"));
            when(dailyMapper.selectOne(any())).thenReturn(null);
            when(dailyMapper.insert(any(DailyAttendance.class))).thenReturn(1);

            service.compute(req());

            ArgumentCaptor<DailyAttendance> captor = ArgumentCaptor.forClass(DailyAttendance.class);
            verify(dailyMapper).insert(captor.capture());
            DailyAttendance saved = captor.getValue();
            assertEquals(AttendanceCalculator.STATUS_HOLIDAY, saved.getAttStatus());
            assertEquals(0, new BigDecimal("2.00").compareTo(saved.getOvertimeHours()),
                    "节假日加班时长链路不受影响");
            verify(calculator, never()).resolveStatus(any(), any(), any(), any());
        }

        @Test
        @DisplayName("非节假日（普通工作日）→ 走既有 resolveStatus 链路，不受接线影响")
        void weekdayKeepsExistingPath() {
            stubNoClosing();
            when(punchService.findByDate(EMP_ID, WORK_DATE)).thenReturn(List.of());
            when(holidayService.isWorkday(WORK_DATE)).thenReturn(
                    new WorkdayCheckResponse(WORK_DATE, true, "WEEKDAY"));
            stubShift();
            stubBaseCalc();
            when(calculator.resolveStatus(any(), any(), any(), any()))
                    .thenReturn(AttendanceCalculator.STATUS_LATE);

            DailyComputeResult result = service.compute(req());

            assertEquals(1, result.lateCount());
            verify(calculator).resolveStatus(any(), any(), any(), any());
            assertEquals(0, result.newExceptionCount());
        }
    }
}

package com.hris.attendance.service;

import com.hris.attendance.dto.DailyDtos.DailyComputeRequest;
import com.hris.attendance.dto.DailyDtos.DailyComputeResult;
import com.hris.attendance.dto.HolidayDtos.WorkdayCheckResponse;
import com.hris.attendance.entity.DailyAttendance;
import com.hris.attendance.entity.PunchRecord;
import com.hris.attendance.entity.Shift;
import com.hris.attendance.entity.ShiftAssignment;
import com.hris.attendance.mapper.DailyAttendanceMapper;
import com.hris.attendance.mapper.MonthlyClosingMapper;
import com.hris.attendance.mapper.PayrollCycleMapper;
import com.hris.attendance.mapper.ShiftAssignmentMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日考勤的节假日语义单测（§8.5-7 法定节假日接线、批6#10 考勤组绑定日历年度）。
 *
 * <p>从 DailyAttendanceServiceTest 拆分而来（单文件 ≤300 行门禁），夹具与原类一致。
 * 锁定：法定节假日不判旷工/迟到/早退（状态 HOLIDAY，加班链路不受影响）；
 * 工作日期不在组绑定年度内则回退星期推断。</p>
 */
@ExtendWith(MockitoExtension.class)
class DailyAttendanceHolidaySemanticsTest {

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
        // 默认员工未加入考勤组 → 降级租户并集（旧行为：日历对全部年度生效）
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
            verify(calculator, never()).resolveStatus(any(), any(), any(), any(), anyBoolean(), anyInt());
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
            verify(calculator, never()).resolveStatus(any(), any(), any(), any(), anyBoolean(), anyInt());
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
            when(calculator.resolveStatus(any(), any(), any(), any(), anyBoolean(), anyInt()))
                    .thenReturn(AttendanceCalculator.STATUS_LATE);

            DailyComputeResult result = service.compute(req());

            assertEquals(1, result.lateCount());
            verify(calculator).resolveStatus(any(), any(), any(), any(), anyBoolean(), anyInt());
            assertEquals(0, result.newExceptionCount());
        }
    }

    @Nested
    @DisplayName("考勤组绑定节假日历年度（B-5）")
    class CalendarYearBinding {

        @Test
        @DisplayName("组绑定 2026 年 + 工作日在 2026 年 + 日历配置 HOLIDAY → 法定节假日生效")
        void holidayAppliesWithinBoundYear() {
            stubNoClosing();
            when(groupService.boundCalendarYearsForEmp(EMP_ID)).thenReturn(List.of(2026));
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
        @DisplayName("组绑定 2026 年 + 工作日在 2027 年 → 日历不适用，回退星期推断（不查节假日历）")
        void calendarSkippedForUnboundYear() {
            LocalDate workDate2027 = LocalDate.parse("2027-01-05");
            stubNoClosing();
            when(groupService.boundCalendarYearsForEmp(EMP_ID)).thenReturn(List.of(2026));
            // 有打卡才不会被「无排班无打卡=休息日」短路，进而走到 resolveStatus 链路
            PunchRecord punch = new PunchRecord();
            punch.setEmpId(EMP_ID);
            punch.setType(PunchService.TYPE_IN);
            punch.setPunchTime(LocalDateTime.parse("2027-01-05T09:00:00"));
            when(punchService.findByDate(EMP_ID, workDate2027)).thenReturn(List.of(punch));
            when(assignmentMapper.selectOne(any())).thenReturn(null);
            stubBaseCalcFor(workDate2027);
            when(calculator.resolveStatus(any(), any(), any(), any(), anyBoolean(), anyInt()))
                    .thenReturn(AttendanceCalculator.STATUS_NORMAL);

            service.compute(new DailyComputeRequest(workDate2027, List.of(EMP_ID)));

            verify(holidayService, never()).isWorkday(any());
            verify(calculator).resolveStatus(any(), any(), any(), any(), anyBoolean(), anyInt());
        }

        @Test
        @DisplayName("调休补班日（source=WORKDAY）→ 不判节假日，走正常出勤判定链路")
        void adjustedWorkdayGoesThroughNormalPath() {
            stubNoClosing();
            when(punchService.findByDate(EMP_ID, WORK_DATE)).thenReturn(List.of());
            when(holidayService.isWorkday(WORK_DATE)).thenReturn(
                    new WorkdayCheckResponse(WORK_DATE, true, "WORKDAY"));
            stubShift();
            stubBaseCalc();
            when(calculator.resolveStatus(any(), any(), any(), any(), anyBoolean(), anyInt()))
                    .thenReturn(AttendanceCalculator.STATUS_NORMAL);

            DailyComputeResult result = service.compute(req());

            assertEquals(1, result.normalCount());
            verify(calculator).resolveStatus(any(), any(), any(), any(), anyBoolean(), anyInt());
        }

        private void stubBaseCalcFor(LocalDate workDate) {
            when(leaveQueryService.leaveHoursOn(EMP_ID, workDate)).thenReturn(BigDecimal.ZERO);
            when(overtimeService.overtimeHoursOn(EMP_ID, workDate)).thenReturn(BigDecimal.ZERO);
            when(dailyMapper.selectOne(any())).thenReturn(null);
            when(dailyMapper.insert(any(DailyAttendance.class))).thenReturn(1);
        }
    }
}

package com.hris.attendance.service;

import com.hris.attendance.entity.AttendanceException;
import com.hris.attendance.entity.DailyAttendance;
import com.hris.attendance.entity.Shift;
import com.hris.attendance.mapper.AttendanceExceptionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AttendanceCalculator 单元测试（ATT-F-008/009 考勤口径）。
 *
 * <p>状态判定优先级：请假满 8h > 无排班 > 无打卡 > 迟到（15 分钟宽限）> 早退 > 正常；
 * 异常类型由状态推导，且「同人同日同类型已存在则跳过」保证重算幂等。
 * 这些阈值是报表/月结/算薪共用的口径，改动必须同步本测试。</p>
 */
@ExtendWith(MockitoExtension.class)
class AttendanceCalculatorTest {

    @Mock
    private AttendanceExceptionMapper exceptionMapper;

    private AttendanceCalculator calculator;

    private AttendanceCalculator newCalculator() {
        return new AttendanceCalculator(exceptionMapper);
    }

    private static Shift shift(String start, String end) {
        Shift s = new Shift();
        s.setStartTime(LocalTime.parse(start));
        s.setEndTime(LocalTime.parse(end));
        return s;
    }

    private static LocalDateTime at(String time) {
        return LocalDateTime.parse("2026-10-16T" + time);
    }

    @Nested
    @DisplayName("状态判定优先级")
    class ResolveStatus {

        @Test
        @DisplayName("请假满 8 小时 → LEAVE（优先级最高，即使有打卡）")
        void leaveHasHighestPriority() {
            assertEquals(AttendanceCalculator.STATUS_LEAVE,
                    newCalculator().resolveStatus(shift("09:00", "18:00"),
                            at("09:00"), at("18:00"), new BigDecimal("8.0")));
        }

        @Test
        @DisplayName("请假不足 8 小时 → 不判 LEAVE，继续按打卡判定")
        void partialLeaveFallsThrough() {
            assertEquals(AttendanceCalculator.STATUS_NORMAL,
                    newCalculator().resolveStatus(shift("09:00", "18:00"),
                            at("09:00"), at("18:00"), new BigDecimal("4.0")));
        }

        @Test
        @DisplayName("无排班且无打卡 → ABSENT；无排班但有打卡 → NORMAL")
        void noShift() {
            assertEquals(AttendanceCalculator.STATUS_ABSENT,
                    newCalculator().resolveStatus(null, null, null, BigDecimal.ZERO));
            assertEquals(AttendanceCalculator.STATUS_NORMAL,
                    newCalculator().resolveStatus(null, at("09:00"), null, BigDecimal.ZERO));
        }

        @Test
        @DisplayName("有排班但全天无打卡 → ABSENT")
        void noPunchWithShift() {
            assertEquals(AttendanceCalculator.STATUS_ABSENT,
                    newCalculator().resolveStatus(shift("09:00", "18:00"), null, null, BigDecimal.ZERO));
        }
    }

    @Nested
    @DisplayName("迟到宽限与早退")
    class GraceAndEarly {

        @ParameterizedTest(name = "上班打卡 {0}（班次 09:00，宽限 15 分钟）→ {1}")
        @CsvSource({
                "09:00, NORMAL",
                "09:15, NORMAL",
                "09:16, LATE",
                "09:30, LATE"
        })
        @DisplayName("迟到判定：超过 09:15 才算迟到")
        void lateWithGrace(String punchIn, String expected) {
            assertEquals(expected,
                    newCalculator().resolveStatus(shift("09:00", "18:00"),
                            at(punchIn), at("18:00"), BigDecimal.ZERO));
        }

        @Test
        @DisplayName("早退：下班打卡早于班次下班时刻 → EARLY")
        void earlyLeave() {
            assertEquals(AttendanceCalculator.STATUS_EARLY,
                    newCalculator().resolveStatus(shift("09:00", "18:00"),
                            at("09:00"), at("17:59"), BigDecimal.ZERO));
        }

        @Test
        @DisplayName("迟到优先于早退（同时命中时判 LATE）")
        void lateWinsOverEarly() {
            assertEquals(AttendanceCalculator.STATUS_LATE,
                    newCalculator().resolveStatus(shift("09:00", "18:00"),
                            at("09:30"), at("17:00"), BigDecimal.ZERO));
        }
    }

    @Nested
    @DisplayName("异常单生成与去重")
    class RaiseExceptions {

        private DailyAttendance daily(String status, String in, String out) {
            DailyAttendance d = new DailyAttendance();
            d.setId(900001L);
            d.setEmpId(200001L);
            d.setAttStatus(status);
            d.setPunchIn(in == null ? null : at(in));
            d.setPunchOut(out == null ? null : at(out));
            return d;
        }

        @Test
        @DisplayName("迟到 → 新增 LATE 异常（去重查询命中 0）")
        void raisesLate() {
            when(exceptionMapper.selectCount(any())).thenReturn(0L);
            when(exceptionMapper.insert(any(AttendanceException.class))).thenReturn(1);

            int created = newCalculator().raiseExceptions(
                    daily(AttendanceCalculator.STATUS_LATE, "09:30", "18:00"),
                    shift("09:00", "18:00"));

            assertEquals(1, created);
            verify(exceptionMapper).insert(any(AttendanceException.class));
        }

        @Test
        @DisplayName("已存在同类异常 → 跳过（重算幂等，不重复生成待办）")
        void dedupSkipsExisting() {
            when(exceptionMapper.selectCount(any())).thenReturn(1L);

            int created = newCalculator().raiseExceptions(
                    daily(AttendanceCalculator.STATUS_ABSENT, null, null), null);

            assertEquals(0, created);
            verify(exceptionMapper, never()).insert(any(AttendanceException.class));
        }

        @Test
        @DisplayName("正常出勤但缺下班卡 → 缺卡异常")
        void normalWithMissingPunchRaises() {
            when(exceptionMapper.selectCount(any())).thenReturn(0L);
            when(exceptionMapper.insert(any(AttendanceException.class))).thenReturn(1);

            int created = newCalculator().raiseExceptions(
                    daily(AttendanceCalculator.STATUS_NORMAL, "09:00", null), null);

            assertEquals(1, created);
        }

        @Test
        @DisplayName("正常出勤且打卡齐全 → 不产生异常")
        void normalFullPunchNoException() {
            assertEquals(0, newCalculator().raiseExceptions(
                    daily(AttendanceCalculator.STATUS_NORMAL, "09:00", "18:00"), null));
            verify(exceptionMapper, never()).insert(any(AttendanceException.class));
        }
    }
}

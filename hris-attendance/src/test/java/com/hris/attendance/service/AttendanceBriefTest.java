package com.hris.attendance.service;

import com.hris.attendance.dto.DailyDtos.DailyAttendanceBriefItem;
import com.hris.attendance.entity.DailyAttendance;
import com.hris.attendance.mapper.DailyAttendanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * attendance-brief 聚合单元测试（批6#10 跨 agent 契约）。
 *
 * <p>契约签名：GET /internal/att/attendance-brief?empIds=1,2&periodStart=..&periodEnd=..
 * 返回 R&lt;List&lt;{empId, lateCount, absentDays}&gt;&gt;；无记录员工返回 0 值项。</p>
 */
@ExtendWith(MockitoExtension.class)
class AttendanceBriefTest {

    private static final LocalDate START = LocalDate.parse("2026-10-01");
    private static final LocalDate END = LocalDate.parse("2026-10-31");

    @Mock
    private DailyAttendanceMapper dailyMapper;

    private DailyAttendanceService service;

    @BeforeEach
    void setUp() {
        // attendanceBrief 只依赖 dailyMapper，其余依赖传 null 不会被触碰
        service = new DailyAttendanceService(dailyMapper, null, null, null, null, null,
                null, null, null, null, null);
    }

    private static DailyAttendance daily(Long empId, String status) {
        DailyAttendance d = new DailyAttendance();
        d.setEmpId(empId);
        d.setAttStatus(status);
        return d;
    }

    @Test
    @DisplayName("按员工聚合迟到次数与全天缺勤天数")
    void aggregatesLateAndAbsent() {
        when(dailyMapper.selectList(any())).thenReturn(List.of(
                daily(1L, AttendanceCalculator.STATUS_LATE),
                daily(1L, AttendanceCalculator.STATUS_LATE),
                daily(1L, AttendanceCalculator.STATUS_NORMAL),
                daily(2L, AttendanceCalculator.STATUS_ABSENT),
                daily(2L, AttendanceCalculator.STATUS_EARLY)));

        List<DailyAttendanceBriefItem> brief = service.attendanceBrief(
                List.of(1L, 2L), START, END);

        assertEquals(2, brief.size());
        assertEquals(1L, brief.get(0).empId());
        assertEquals(2, brief.get(0).lateCount());
        assertEquals(0, brief.get(0).absentDays());
        assertEquals(2L, brief.get(1).empId());
        assertEquals(0, brief.get(1).lateCount());
        assertEquals(1, brief.get(1).absentDays());
    }

    @Test
    @DisplayName("无任何日考勤记录的员工返回 0 值项")
    void missingEmployeeGetsZeroItem() {
        when(dailyMapper.selectList(any())).thenReturn(List.of());

        List<DailyAttendanceBriefItem> brief = service.attendanceBrief(
                List.of(1L, 2L, 3L), START, END);

        assertEquals(3, brief.size());
        for (DailyAttendanceBriefItem item : brief) {
            assertEquals(0, item.lateCount());
            assertEquals(0, item.absentDays());
        }
    }

    @Test
    @DisplayName("重复 empIds 去重；结果顺序与请求 empIds 一致")
    void dedupsAndKeepsRequestOrder() {
        when(dailyMapper.selectList(any())).thenReturn(List.of(
                daily(2L, AttendanceCalculator.STATUS_LATE)));

        List<DailyAttendanceBriefItem> brief = service.attendanceBrief(
                List.of(2L, 1L, 2L), START, END);

        assertEquals(List.of(2L, 1L), brief.stream().map(DailyAttendanceBriefItem::empId).toList());
        assertEquals(1, brief.get(0).lateCount());
        assertEquals(0, brief.get(1).lateCount());
    }
}

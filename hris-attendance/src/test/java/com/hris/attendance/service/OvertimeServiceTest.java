package com.hris.attendance.service;

import com.hris.attendance.dto.HolidayDtos.WorkdayCheckResponse;
import com.hris.attendance.entity.OvertimeRequest;
import com.hris.attendance.mapper.OvertimeRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * OvertimeService 倍率单测（§8.5-7 节假日接线）。
 *
 * <p>倍率口径：法定节假日 3.0 / 休息日 2.0 / 工作日 1.5（含调休补班日）。
 * 日期类型由节假日历（att_holiday）经 HolidayService.isWorkday 判定，
 * 日历配置优先于星期推断。</p>
 */
@ExtendWith(MockitoExtension.class)
class OvertimeServiceTest {

    private static final Long OT_ID = 400001L;

    @Mock
    private OvertimeRequestMapper overtimeMapper;
    @Mock
    private FlowIntegration flowIntegration;
    @Mock
    private HolidayService holidayService;

    private OvertimeService service;

    @BeforeEach
    void setUp() {
        service = new OvertimeService(overtimeMapper, flowIntegration, holidayService);
    }

    private void stubOvertime(LocalDate startDate) {
        OvertimeRequest ot = new OvertimeRequest();
        ot.setId(OT_ID);
        ot.setEmpId(200001L);
        ot.setStartTime(startDate.atTime(18, 0));
        ot.setEndTime(startDate.atTime(21, 0));
        ot.setHours(new BigDecimal("3.0"));
        ot.setStatus(OvertimeService.STATUS_APPROVED);
        when(overtimeMapper.selectById(OT_ID)).thenReturn(ot);
    }

    @Test
    @DisplayName("法定节假日（日历 HOLIDAY）→ 倍率 3.00")
    void holidayRateIsThree() {
        LocalDate date = LocalDate.parse("2026-10-01");
        stubOvertime(date);
        when(holidayService.isWorkday(date))
                .thenReturn(new WorkdayCheckResponse(date, false, "HOLIDAY"));

        var resp = service.get(OT_ID);

        assertEquals("HOLIDAY", resp.dayType());
        assertEquals(0, new BigDecimal("3.00").compareTo(resp.rate()));
    }

    @Test
    @DisplayName("调休补班日（周末但日历 WORKDAY）→ 按工作日倍率 1.50")
    void makeupWorkdayRateIsOnePointFive() {
        LocalDate date = LocalDate.parse("2026-09-26");
        stubOvertime(date);
        when(holidayService.isWorkday(date))
                .thenReturn(new WorkdayCheckResponse(date, true, "WORKDAY"));

        var resp = service.get(OT_ID);

        assertEquals("WEEKDAY", resp.dayType());
        assertEquals(0, new BigDecimal("1.50").compareTo(resp.rate()));
    }

    @Test
    @DisplayName("普通周末（无日历记录）→ 倍率 2.00")
    void weekendRateIsTwo() {
        LocalDate date = LocalDate.parse("2026-10-17");
        stubOvertime(date);
        when(holidayService.isWorkday(date))
                .thenReturn(new WorkdayCheckResponse(date, false, "WEEKDAY"));

        var resp = service.get(OT_ID);

        assertEquals("WEEKEND", resp.dayType());
        assertEquals(0, new BigDecimal("2.00").compareTo(resp.rate()));
    }

    @Test
    @DisplayName("普通工作日（无日历记录）→ 倍率 1.50")
    void weekdayRateIsOnePointFive() {
        LocalDate date = LocalDate.parse("2026-10-16");
        stubOvertime(date);
        when(holidayService.isWorkday(date))
                .thenReturn(new WorkdayCheckResponse(date, true, "WEEKDAY"));

        var resp = service.get(OT_ID);

        assertEquals("WEEKDAY", resp.dayType());
        assertEquals(0, new BigDecimal("1.50").compareTo(resp.rate()));
    }
}

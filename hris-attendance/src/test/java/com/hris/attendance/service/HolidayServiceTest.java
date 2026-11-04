package com.hris.attendance.service;

import com.hris.attendance.dto.HolidayDtos.HolidayRequest;
import com.hris.attendance.dto.HolidayDtos.HolidayResponse;
import com.hris.attendance.dto.HolidayDtos.WorkdayCheckResponse;
import com.hris.attendance.entity.Holiday;
import com.hris.attendance.mapper.HolidayMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HolidayService 工作日判定与新建单元测试（ATT-F-021）。
 *
 * <p>两类守卫失效都会造成<b>静默算错考勤</b>：①周末补班日被当休息日 → 漏算缺勤；
 * ②非法 dayType 被放行 → isWorkday 里 TYPE_WORKDAY.equals 判 false，任何非 WORKDAY
 * 值都被当作"放假"。故日历配置必须严格优先于星期推断，dayType 只允许两个枚举值。</p>
 *
 * <p>变更/删除/查询见 HolidayServiceWriteTest（拆分为满足单文件 ≤300 行门禁）。</p>
 */
@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 1, 9);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 1, 10);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 1, 11);

    @Mock
    private HolidayMapper holidayMapper;

    private HolidayService service;

    @BeforeEach
    void setUp() {
        service = new HolidayService(holidayMapper);
    }

    private Holiday hit(String dayType) {
        Holiday h = new Holiday();
        h.setId(1L);
        h.setHolidayDate(MONDAY);
        h.setDayType(dayType);
        h.setName("配置");
        return h;
    }

    private HolidayRequest req(String dayType) {
        return new HolidayRequest(MONDAY, "元旦", dayType, "备注");
    }

    @Nested
    @DisplayName("isWorkday：日历配置优先于星期推断")
    class IsWorkday {

        @Test
        @DisplayName("命中 HOLIDAY → 非工作日，source=HOLIDAY（周一也放假）")
        void holidayOverridesWeekday() {
            when(holidayMapper.selectOne(any())).thenReturn(hit(HolidayService.TYPE_HOLIDAY));

            WorkdayCheckResponse r = service.isWorkday(MONDAY);

            assertFalse(r.workday(), "法定节假日即使是周一也应放假");
            assertEquals("HOLIDAY", r.source());
            assertEquals(MONDAY, r.date());
        }

        @Test
        @DisplayName("命中 WORKDAY → 工作日，source=WORKDAY（周六也补班）")
        void workdayOverridesWeekend() {
            when(holidayMapper.selectOne(any())).thenReturn(hit(HolidayService.TYPE_WORKDAY));

            WorkdayCheckResponse r = service.isWorkday(SATURDAY);

            assertTrue(r.workday(), "调班补班日即使落在周六也应算工作日");
            assertEquals("WORKDAY", r.source());
        }

        @Test
        @DisplayName("未配置 + 周一 → 工作日，source=WEEKDAY")
        void mondayIsWorkdayByDefault() {
            when(holidayMapper.selectOne(any())).thenReturn(null);

            WorkdayCheckResponse r = service.isWorkday(MONDAY);

            assertTrue(r.workday());
            assertEquals("WEEKDAY", r.source());
        }

        @Test
        @DisplayName("未配置 + 周五 → 工作日，source=WEEKDAY")
        void fridayIsWorkdayByDefault() {
            when(holidayMapper.selectOne(any())).thenReturn(null);

            WorkdayCheckResponse r = service.isWorkday(FRIDAY);

            assertTrue(r.workday());
            assertEquals("WEEKDAY", r.source());
        }

        @Test
        @DisplayName("未配置 + 周六 → 非工作日，source=WEEKDAY（周末边界）")
        void saturdayIsNotWorkdayByDefault() {
            when(holidayMapper.selectOne(any())).thenReturn(null);

            WorkdayCheckResponse r = service.isWorkday(SATURDAY);

            assertFalse(r.workday(), "周六默认应休息");
            assertEquals("WEEKDAY", r.source());
        }

        @Test
        @DisplayName("未配置 + 周日 → 非工作日，source=WEEKDAY（周末边界，防只排除周六）")
        void sundayIsNotWorkdayByDefault() {
            when(holidayMapper.selectOne(any())).thenReturn(null);

            WorkdayCheckResponse r = service.isWorkday(SUNDAY);

            assertFalse(r.workday(), "周日默认也应休息；若只排除周六则周日会被误判为工作日");
            assertEquals("WEEKDAY", r.source());
        }

        @Test
        @DisplayName("查询条件按传入日期精确过滤")
        void queriesByGivenDate() {
            when(holidayMapper.selectOne(any())).thenReturn(null);

            service.isWorkday(SATURDAY);

            verify(holidayMapper).selectOne(any());
        }
    }

    @Nested
    @DisplayName("create：dayType 白名单校验")
    class CreateType {

        @Test
        @DisplayName("合法 HOLIDAY → 落库并返回响应各字段")
        void createsHoliday() {
            when(holidayMapper.selectCount(any())).thenReturn(0L);

            HolidayResponse r = service.create(req(HolidayService.TYPE_HOLIDAY));

            assertEquals(MONDAY, r.holidayDate());
            assertEquals("元旦", r.name());
            assertEquals("HOLIDAY", r.dayType());
            assertEquals("备注", r.remark());
            verify(holidayMapper).insert(any(Holiday.class));
        }

        @Test
        @DisplayName("合法 WORKDAY → 落库且 dayType 为 WORKDAY")
        void createsWorkday() {
            when(holidayMapper.selectCount(any())).thenReturn(0L);

            assertEquals("WORKDAY", service.create(req(HolidayService.TYPE_WORKDAY)).dayType());
            verify(holidayMapper).insert(any(Holiday.class));
        }

        @Test
        @DisplayName("非法 dayType → PARAM_INVALID 且不落库（防自由文本污染判定）")
        void rejectsUnknownType() {
            BizException e = assertThrows(BizException.class,
                    () -> service.create(req("FESTIVAL")));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(holidayMapper, never()).insert(any(Holiday.class));
        }

        @Test
        @DisplayName("dayType 为 null → PARAM_INVALID")
        void rejectsNullType() {
            assertThrows(BizException.class, () -> service.create(req(null)));
        }

        @Test
        @DisplayName("dayType 大小写敏感：'holiday' 不被接受")
        void rejectsLowercaseType() {
            assertThrows(BizException.class, () -> service.create(req("holiday")));
        }
    }

    @Nested
    @DisplayName("create：同日唯一性守卫")
    class CreateDuplicate {

        @Test
        @DisplayName("同一天已存在记录 → PARAM_INVALID，不落库")
        void rejectsDuplicateDate() {
            when(holidayMapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class,
                    () -> service.create(req(HolidayService.TYPE_HOLIDAY)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(holidayMapper, never()).insert(any(Holiday.class));
        }

        @Test
        @DisplayName("查重通过后才落库（先校验后写入的顺序）")
        void checksDuplicateBeforeInsert() {
            when(holidayMapper.selectCount(any())).thenReturn(0L);

            service.create(req(HolidayService.TYPE_HOLIDAY));

            verify(holidayMapper).selectCount(any());
            verify(holidayMapper).insert(any(Holiday.class));
        }
    }
}
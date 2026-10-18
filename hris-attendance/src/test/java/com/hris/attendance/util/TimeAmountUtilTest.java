package com.hris.attendance.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TimeAmountUtil 单元测试（纯逻辑，无 Spring 上下文）。
 *
 * <p>覆盖口径：毫秒 → 小时（2 位 HALF_UP）→ 天（8h/天）的四处共用换算，
 * 重点是边界（零/负区间、空值）与四舍五入方向，确保请假/加班/月结对账口径一致。</p>
 */
class TimeAmountUtilTest {

    @Nested
    @DisplayName("hoursBetween")
    class HoursBetween {

        @Test
        @DisplayName("整小时区间精确换算")
        void wholeHours() {
            BigDecimal h = TimeAmountUtil.hoursBetween(
                    LocalDateTime.parse("2026-10-20T09:00:00"),
                    LocalDateTime.parse("2026-10-20T18:00:00"));
            assertEquals(0, h.compareTo(new BigDecimal("9.00")));
        }

        @Test
        @DisplayName("分钟按 60 进制 HALF_UP 进位（90 分钟 = 1.50 小时）")
        void halfUpOnMinutes() {
            BigDecimal h = TimeAmountUtil.hoursBetween(
                    LocalDateTime.parse("2026-10-20T09:00:00"),
                    LocalDateTime.parse("2026-10-20T10:30:00"));
            assertEquals(0, h.compareTo(new BigDecimal("1.50")));
        }

        @Test
        @DisplayName("非正数区间（相等/倒挂）返回 0.00")
        void nonPositiveReturnsZero() {
            LocalDateTime t = LocalDateTime.parse("2026-10-20T09:00:00");
            assertEquals(0, TimeAmountUtil.hoursBetween(t, t).compareTo(BigDecimal.ZERO));
            assertEquals(0, TimeAmountUtil.hoursBetween(t, t.minusHours(1)).compareTo(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("空值入参返回 0.00")
        void nullArgsReturnZero() {
            LocalDateTime t = LocalDateTime.parse("2026-10-20T09:00:00");
            assertEquals(0, TimeAmountUtil.hoursBetween(null, t).compareTo(BigDecimal.ZERO));
            assertEquals(0, TimeAmountUtil.hoursBetween(t, null).compareTo(BigDecimal.ZERO));
        }
    }

    @Nested
    @DisplayName("toDays")
    class ToDays {

        @Test
        @DisplayName("8 小时 = 1 天")
        void eightHoursIsOneDay() {
            assertEquals(0, TimeAmountUtil.toDays(new BigDecimal("8")).compareTo(new BigDecimal("1.00")));
        }

        @Test
        @DisplayName("4 小时 = 0.5 天；空值按 0 处理")
        void halfDayAndNull() {
            assertEquals(0, TimeAmountUtil.toDays(new BigDecimal("4")).compareTo(new BigDecimal("0.50")));
            assertEquals(0, TimeAmountUtil.toDays(null).compareTo(BigDecimal.ZERO));
        }
    }

    @Test
    @DisplayName("amount 统一 2 位小数 HALF_UP，空值按 0")
    void amountNormalizes() {
        assertEquals(0, TimeAmountUtil.amount(new BigDecimal("1.005")).compareTo(new BigDecimal("1.01")));
        assertEquals(0, TimeAmountUtil.amount(null).compareTo(BigDecimal.ZERO));
    }
}

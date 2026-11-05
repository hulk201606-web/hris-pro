package com.hris.compensation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 入离职当日折算单测（CMP-F-011）：两态开关差异 + 整月不折算 + 无交集归零。
 */
class SalaryProrationServiceTest {

    private static final YearMonth JAN = YearMonth.of(2026, 1);
    private static final BigDecimal BASE = new BigDecimal("10000");

    private final SalaryProrationService service = new SalaryProrationService();

    @Test
    @DisplayName("整月覆盖（无入离职）→ 原值直出，不产生小数位漂移")
    void fullMonthKeepsBase() {
        BigDecimal result = service.prorate(BASE, LocalDate.of(2025, 12, 1), null, JAN, true);

        assertEquals("10000", result.toPlainString());
    }

    @Test
    @DisplayName("月中入职（1/16 入职，入职当日恒计薪）→ 16/31 折算")
    void midMonthHire() {
        BigDecimal result = service.prorate(BASE, LocalDate.of(2026, 1, 16), null, JAN, true);

        assertEquals(new BigDecimal("5161.29"), result);
    }

    @Test
    @DisplayName("离职当日计薪=true（1/15 离职）→ 15/31 折算")
    void terminationDayPaid() {
        BigDecimal result = service.prorate(BASE, null, LocalDate.of(2026, 1, 15), JAN, true);

        assertEquals(new BigDecimal("4838.71"), result);
    }

    @Test
    @DisplayName("离职当日计薪=false（1/15 离职）→ 14/31 折算，较 true 少一天")
    void terminationDayUnpaid() {
        BigDecimal paid = service.prorate(BASE, null, LocalDate.of(2026, 1, 15), JAN, true);
        BigDecimal unpaid = service.prorate(BASE, null, LocalDate.of(2026, 1, 15), JAN, false);

        assertEquals(new BigDecimal("4516.13"), unpaid);
        assertEquals(new BigDecimal("322.58"), paid.subtract(unpaid));
    }

    @Test
    @DisplayName("离职日为本月 1 日且不计薪 → 0")
    void terminateOnFirstDayUnpaid() {
        BigDecimal result = service.prorate(BASE, null, LocalDate.of(2026, 1, 1), JAN, false);

        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    @DisplayName("定薪区间与周期无交集 → 0")
    void noOverlap() {
        assertEquals(BigDecimal.ZERO,
                service.prorate(BASE, LocalDate.of(2026, 2, 1), null, JAN, true));
        assertEquals(BigDecimal.ZERO,
                service.prorate(BASE, null, LocalDate.of(2025, 12, 31), JAN, true));
    }

    @Test
    @DisplayName("base 为 null → 0")
    void nullBase() {
        assertEquals(BigDecimal.ZERO, service.prorate(null, null, null, JAN, true));
    }
}

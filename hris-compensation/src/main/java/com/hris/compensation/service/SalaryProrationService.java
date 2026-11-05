package com.hris.compensation.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * 入离职当日折算（CMP-F-011）。
 *
 * <p>口径：按计薪周期「日历天数」折算定薪基数——应计天数 = 定薪有效期与周期交集天数，
 * 基数 × 应计天数 / 周期总天数（保留 2 位，HALF_UP）。整月覆盖时不折算，原值直出
 * （避免 10000 → 10000.00 的字符串漂移，保证明细可复算一致）。</p>
 *
 * <p>边界规则：
 * <ul>
 *   <li>入职当日：恒计 1 天（首日上班即计薪，不做开关）；</li>
 *   <li>离职当日：由租户开关 hris.cmp.calc.pay-termination-day 决定——
 *       true（缺省）当日按 1 天计；false 则截止到前一日（当日不计）。</li>
 * </ul>
 * 窗口来源：cmp_employee_pay.effective_from / effective_to。cmp 域内无独立入离职日期表，
 * 定薪生效区间即「起薪日/停薪日」，与算薪取数 loadEffectiveEmpPays 同口径。</p>
 */
@Component
public class SalaryProrationService {

    /**
     * 折算定薪基数。
     *
     * @param base              定薪金额（明文）
     * @param effectiveFrom     起薪日（null=周期前已在职）
     * @param effectiveTo       停薪日（null=周期后仍在职）
     * @param period            计薪周期
     * @param payTerminationDay 离职当日是否计薪
     */
    public BigDecimal prorate(BigDecimal base, LocalDate effectiveFrom, LocalDate effectiveTo,
                              YearMonth period, boolean payTerminationDay) {
        if (base == null) {
            return BigDecimal.ZERO;
        }
        int totalDays = period.lengthOfMonth();
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();
        LocalDate start = effectiveFrom != null && effectiveFrom.isAfter(periodStart)
                ? effectiveFrom : periodStart;
        LocalDate end = effectiveTo != null && effectiveTo.isBefore(periodEnd)
                ? effectiveTo : periodEnd;
        // 定薪区间与周期无交集：当期不计薪
        if (start.isAfter(periodEnd) || end.isBefore(periodStart)) {
            return BigDecimal.ZERO;
        }
        // 离职当日不计薪：窗口截止到离职前一日（仅当离职日落在本周期内）
        if (!payTerminationDay && effectiveTo != null
                && !effectiveTo.isBefore(periodStart) && !effectiveTo.isAfter(periodEnd)) {
            end = effectiveTo.minusDays(1);
        }
        long payableDays = ChronoUnit.DAYS.between(start, end) + 1;
        if (payableDays <= 0) {
            return BigDecimal.ZERO;
        }
        if (payableDays >= totalDays) {
            return base;
        }
        return base.multiply(BigDecimal.valueOf(payableDays))
                .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
    }
}

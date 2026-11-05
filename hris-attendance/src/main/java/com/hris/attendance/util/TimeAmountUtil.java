package com.hris.attendance.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 时间金额工具类（域：考勤，分层：工具/Util）。统一 BigDecimal 时长归一化（null→0、保留 2 位小数），避免 NPE 与精度漂移。
 */
public final class TimeAmountUtil {

    /** 一个标准工作日折算小时数 */
    public static final int HOURS_PER_DAY = 8;

    private static final int SCALE = 2;

    private static final ObjectMapper JSON = new ObjectMapper();

    private TimeAmountUtil() {
    }

    /** 计算小时数（2 位小数，HALF_UP）；非正数区间返回 0。 */
    public static BigDecimal hoursBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        long minutes = Duration.between(start, end).toMinutes();
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), SCALE, RoundingMode.HALF_UP);
    }

    /** 小时换算为天（2 位小数，HALF_UP）。 */
    public static BigDecimal toDays(BigDecimal hours) {
        BigDecimal h = hours == null ? BigDecimal.ZERO : hours;
        return h.divide(BigDecimal.valueOf(HOURS_PER_DAY), SCALE, RoundingMode.HALF_UP);
    }

    /** 统一数值口径：2 位小数 HALF_UP，空值按 0 处理。 */
    public static BigDecimal amount(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 按假种 rules JSON 的 minUnit（B-6：docs/03-03 ATT-F-011 最小单位 0.5h/0.5d）
     * 将请假小时数向上取整到最小单位：额度按保守口径计账，防止碎扣。
     * "0.5d"→4h、"1d"→8h、"0.5h"→0.5h、"1h"→1h；无配置或不可解析时原值返回。
     */
    public static BigDecimal roundUpToMinUnit(BigDecimal hours, String rulesJson) {
        BigDecimal h = amount(hours);
        if (h.compareTo(BigDecimal.ZERO) <= 0 || rulesJson == null || rulesJson.isBlank()) {
            return h;
        }
        BigDecimal unitHours = parseMinUnitHours(rulesJson);
        if (unitHours == null || unitHours.compareTo(BigDecimal.ZERO) <= 0) {
            return h;
        }
        return h.divide(unitHours, 0, RoundingMode.CEILING)
                .multiply(unitHours)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** 解析 rules JSON 的 minUnit 为小时数；非法值返回 null（按未配置处理）。 */
    private static BigDecimal parseMinUnitHours(String rulesJson) {
        try {
            JsonNode node = JSON.readTree(rulesJson);
            String minUnit = node == null ? null : node.path("minUnit").asText(null);
            if (minUnit == null || minUnit.isBlank()) {
                return null;
            }
            String unit = minUnit.trim().toLowerCase(Locale.ROOT);
            if (unit.endsWith("d")) {
                return new BigDecimal(unit.substring(0, unit.length() - 1).trim())
                        .multiply(BigDecimal.valueOf(HOURS_PER_DAY));
            }
            if (unit.endsWith("h")) {
                return new BigDecimal(unit.substring(0, unit.length() - 1).trim());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}

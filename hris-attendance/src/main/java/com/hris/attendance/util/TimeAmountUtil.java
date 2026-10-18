package com.hris.attendance.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 时长换算工具（请假天数 / 加班小时）。
 *
 * <p>说明：与 util/CodeUtil 同属本模块确需的公共无状态工具；请假、加班、月结、报表四处
 * 都要做同一套「毫秒 → 小时（2 位小数 HALF_UP）→ 天（8 小时/天）」换算，
 * 抽出的目的是保证四个域算出来的口径完全一致，避免四舍五入差导致对账不平。</p>
 */
public final class TimeAmountUtil {

    /** 一个标准工作日折算小时数 */
    public static final int HOURS_PER_DAY = 8;

    private static final int SCALE = 2;

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
}

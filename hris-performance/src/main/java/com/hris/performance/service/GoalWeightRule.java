package com.hris.performance.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 目标权重校验（PRF-F-003）。口径：同周期 + 同员工 + 同维度 权重合计 = 100（单位 %）。
 *
 * <p>两档校验：
 * <ul>
 *   <li>单条录入：只拦"合计超 100"（增量录入过程中合计必然 &lt; 100，属正常中间态）；</li>
 *   <li>整体提交（batch，按 cycleId:empId:dimension 整组替换）：要求合计恰好 = 100。</li>
 * </ul>
 * 校验失败统一 PARAM_INVALID，消息内附实际合计值，便于前端直接提示"当前合计 85%"。</p>
 */
public final class GoalWeightRule {

    /** 权重满值。 */
    public static final BigDecimal FULL_WEIGHT = new BigDecimal("100");
    /** 默认维度（历史数据无维度列时一律按 KPI 归档）。 */
    public static final String DEFAULT_DIMENSION = "KPI";
    /** 维度字段长度上限（与 prf_goal.dimension VARCHAR(16) 对齐）。 */
    private static final int DIMENSION_MAX_LEN = 16;

    private GoalWeightRule() {
    }

    /** 维度归一：空 → KPI；统一大写；超长/非法 → PARAM_INVALID。 */
    public static String normalizeDimension(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return DEFAULT_DIMENSION;
        }
        String d = dimension.trim().toUpperCase();
        if (d.length() > DIMENSION_MAX_LEN) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "目标维度超长（上限 " + DIMENSION_MAX_LEN + "）: " + dimension);
        }
        return d;
    }

    /** 单条权重合法性：必须 0 &lt; weight ≤ 100。 */
    public static void requireSingleWeight(BigDecimal weight, String title) {
        if (weight == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "目标权重不能为空: " + title);
        }
        if (weight.signum() <= 0 || weight.compareTo(FULL_WEIGHT) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "目标权重须在 (0, 100] 区间内: " + title + " → " + plain(weight));
        }
    }

    /** 合计。 */
    public static BigDecimal sum(Collection<BigDecimal> weights) {
        BigDecimal total = BigDecimal.ZERO;
        if (weights == null) {
            return total;
        }
        for (BigDecimal w : weights) {
            if (w != null) {
                total = total.add(w);
            }
        }
        return total;
    }

    /** 整体提交：合计必须恰好 100。 */
    public static void requireComplete(BigDecimal total, String scope) {
        if (total.compareTo(FULL_WEIGHT) != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "目标权重合计必须为 100%，当前合计 " + plain(total) + "%（" + scope + "）");
        }
    }

    /** 增量录入：合计不得超过 100。 */
    public static void requireNotExceed(BigDecimal total, String scope) {
        if (total.compareTo(FULL_WEIGHT) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "目标权重合计不得超过 100%，当前合计 " + plain(total) + "%（" + scope + "）");
        }
    }

    /** 批量条目逐条校验（空集合不合法）。 */
    public static void requireBatchItems(List<BigDecimal> weights, List<String> titles, String scope) {
        if (weights == null || weights.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "目标集合不能为空（" + scope + "）");
        }
        for (int i = 0; i < weights.size(); i++) {
            requireSingleWeight(weights.get(i), titles.get(i));
        }
    }

    /** 校验上下文描述（日志/错误消息统一口径）。 */
    public static String scope(Long cycleId, Long empId, String dimension) {
        return "周期 " + cycleId + " / 员工 " + empId + " / 维度 " + dimension;
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}

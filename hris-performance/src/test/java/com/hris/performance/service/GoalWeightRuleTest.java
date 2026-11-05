package com.hris.performance.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权重口径纯函数单测（PRF-F-003）：维度归一、单条区间、合计 = 100 / 不超 100。
 */
@DisplayName("目标权重校验规则")
class GoalWeightRuleTest {

    @Test
    @DisplayName("维度归一：空 → KPI，去空格并大写")
    void normalizeDimension() {
        assertEquals("KPI", GoalWeightRule.normalizeDimension(null));
        assertEquals("KPI", GoalWeightRule.normalizeDimension("  "));
        assertEquals("OKR", GoalWeightRule.normalizeDimension(" okr "));
    }

    @Test
    @DisplayName("维度超长 → PARAM_INVALID")
    void rejectsTooLongDimension() {
        BizException e = assertThrows(BizException.class,
                () -> GoalWeightRule.normalizeDimension("DIMENSION_TOO_LONG_X"));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
    }

    @Test
    @DisplayName("单条权重区间 (0,100]")
    void singleWeightRange() {
        assertDoesNotThrow(() -> GoalWeightRule.requireSingleWeight(new BigDecimal("0.01"), "t"));
        assertDoesNotThrow(() -> GoalWeightRule.requireSingleWeight(new BigDecimal("100"), "t"));
        assertThrows(BizException.class, () -> GoalWeightRule.requireSingleWeight(BigDecimal.ZERO, "t"));
        assertThrows(BizException.class, () -> GoalWeightRule.requireSingleWeight(new BigDecimal("-1"), "t"));
        assertThrows(BizException.class, () -> GoalWeightRule.requireSingleWeight(new BigDecimal("100.01"), "t"));
        assertThrows(BizException.class, () -> GoalWeightRule.requireSingleWeight(null, "t"));
    }

    @Test
    @DisplayName("整体提交：合计必须恰好 100，错误消息带实际合计")
    void requireComplete() {
        assertDoesNotThrow(() -> GoalWeightRule.requireComplete(new BigDecimal("100"), "scope"));

        BizException e = assertThrows(BizException.class,
                () -> GoalWeightRule.requireComplete(new BigDecimal("85.5"),
                        GoalWeightRule.scope(1L, 2L, "KPI")));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("85.5"), "应附实际合计值: " + e.getMessage());
        assertTrue(e.getMessage().contains("周期 1"), "应附校验口径: " + e.getMessage());
    }

    @Test
    @DisplayName("增量录入：合计不得超过 100")
    void requireNotExceed() {
        assertDoesNotThrow(() -> GoalWeightRule.requireNotExceed(new BigDecimal("100"), "s"));
        BizException e = assertThrows(BizException.class,
                () -> GoalWeightRule.requireNotExceed(new BigDecimal("100.01"), "s"));
        assertTrue(e.getMessage().contains("100.01"));
    }

    @Test
    @DisplayName("合计忽略 null，保留两位小数")
    void sumSkipsNull() {
        assertEquals(0, GoalWeightRule.sum(null).compareTo(BigDecimal.ZERO));
        assertEquals(0, GoalWeightRule.sum(Arrays.asList(
                new BigDecimal("33.33"), null, new BigDecimal("66.67")))
                .compareTo(new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("批量条目校验：空集合拒绝，逐条区间校验")
    void batchItems() {
        assertThrows(BizException.class,
                () -> GoalWeightRule.requireBatchItems(List.of(), List.of(), "s"));
        assertDoesNotThrow(() -> GoalWeightRule.requireBatchItems(
                List.of(new BigDecimal("100")), List.of("ok"), "s"));
        assertThrows(BizException.class, () -> GoalWeightRule.requireBatchItems(
                List.of(new BigDecimal("0")), List.of("bad"), "s"));
    }
}

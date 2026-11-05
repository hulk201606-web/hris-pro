package com.hris.employee.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工号模板解析（EMP-F-002）。
 */
class EmpNoPatternTest {

    @Test
    @DisplayName("标准模板：{PREFIX}{YYYY}{SEQ:4} → e20260001")
    void rendersStandardTemplate() {
        assertEquals("e20260001", EmpNoPattern.render("{PREFIX}{YYYY}{SEQ:4}", "E", 2026, 1));
    }

    @Test
    @DisplayName("两位年份 + 前缀小写归一化 + 无宽度序号")
    void rendersShortYearAndPlainSeq() {
        assertEquals("hr2642", EmpNoPattern.render("{PREFIX}{YY}{SEQ}", " HR ", 2026, 42));
    }

    @Test
    @DisplayName("无前缀时回退 e（与旧 CodeUtil 缺省一致）")
    void blankPrefixFallsBackToE() {
        assertEquals("e20260007", EmpNoPattern.render("{PREFIX}{YYYY}{SEQ:4}", null, 2026, 7));
    }

    @Test
    @DisplayName("非法模板：缺 SEQ / 未知占位符 / 畸形占位符 一律不支持")
    void rejectsInvalidPatterns() {
        assertFalse(EmpNoPattern.isSupported("{PREFIX}{YYYY}"));
        assertFalse(EmpNoPattern.isSupported("{PREFIX}{FOO}{SEQ:4}"));
        assertFalse(EmpNoPattern.isSupported("{PREFIX}{SEQ:abc}"));
        assertFalse(EmpNoPattern.isSupported(""));
        assertFalse(EmpNoPattern.isSupported(null));
    }

    @Test
    @DisplayName("合法模板识别与序号宽度")
    void acceptsValidPattern() {
        assertTrue(EmpNoPattern.isSupported("{PREFIX}{YYYY}{SEQ:4}"));
        assertEquals(4, EmpNoPattern.seqWidth("{PREFIX}{YYYY}{SEQ:4}"));
        assertEquals(1, EmpNoPattern.seqWidth("{SEQ}"));
    }

    @Test
    @DisplayName("超长渲染截断到工号列宽 32")
    void truncatesToColumnWidth() {
        String rendered = EmpNoPattern.render("{PREFIX}{YYYY}{YY}{YY}{SEQ:12}",
                "abcdefghijklmnopqrstuvwxyz", 2026, 1);
        assertEquals(32, rendered.length());
    }
}

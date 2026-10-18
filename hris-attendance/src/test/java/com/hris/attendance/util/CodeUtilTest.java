package com.hris.attendance.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CodeUtil 单元测试（纯逻辑）。
 *
 * <p>覆盖：编码 trim/lower 规范化与 bizKey 单号格式（与 att_shift.code 服务端转小写行为对齐，
 * 见联调校准结论 docs/15 §10.2）。</p>
 */
class CodeUtilTest {

    @Test
    @DisplayName("normalize：去首尾空白并转小写；空值透传 null")
    void normalizeTrimsAndLowercases() {
        assertEquals("sh-test", CodeUtil.normalize("  SH-Test  "));
        assertNull(CodeUtil.normalize(null));
    }

    @Test
    @DisplayName("bizKey：规范化前缀 + 冒号 + 雪花 ID（纯数字）")
    void bizKeyFormat() {
        String key = CodeUtil.bizKey(" ATT_Leave ");
        assertTrue(key.startsWith("att_leave:"), "前缀应规范化为小写，实际: " + key);
        String idPart = key.substring(key.indexOf(':') + 1);
        assertTrue(idPart.matches("\\d+"), "雪花 ID 应为纯数字，实际: " + idPart);
    }

    @Test
    @DisplayName("next：规范化前缀 + 雪花 ID，无冒号分隔")
    void nextFormat() {
        String code = CodeUtil.next(" ATT_OT ");
        assertTrue(code.startsWith("att_ot"), "前缀应规范化，实际: " + code);
        assertTrue(code.matches("att_ot\\d+"), "应为前缀+纯数字，实际: " + code);
    }
}

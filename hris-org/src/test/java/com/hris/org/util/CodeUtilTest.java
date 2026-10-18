package com.hris.org.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CodeUtil 单元测试（docs/12 §2 第 11 条：标识符字段统一 trim+lower 规范化）。
 *
 * <p>normalize 的返回值直接喂给 5 个目录服务的 ensureCodeAvailable 做唯一性去重，
 * null/空白处理若失效会导致去重查询错判，因此这是编码唯一性守卫的前置契约。</p>
 */
class CodeUtilTest {

    @Nested
    @DisplayName("normalize：去首尾空白 + 转小写")
    class Normalize {

        @Test
        @DisplayName("去首尾空白并转小写")
        void trimsAndLowercases() {
            assertEquals("abc", CodeUtil.normalize("  ABC  "));
        }

        @Test
        @DisplayName("已是小写时幂等")
        void idempotentOnLowercase() {
            assertEquals("abc", CodeUtil.normalize("abc"));
        }

        @Test
        @DisplayName("null 入参返回 null（不抛 NPE，供调用方走自动生成分支）")
        void nullReturnsNull() {
            assertNull(CodeUtil.normalize(null));
        }

        @Test
        @DisplayName("仅含空白返回空串（调用方以 isBlank 判定后自动生成编码）")
        void blankReturnsEmpty() {
            assertEquals("", CodeUtil.normalize("   "));
        }
    }

    @Nested
    @DisplayName("next：前缀 + 雪花 ID")
    class Next {

        @Test
        @DisplayName("生成结果以规范化后的前缀开头")
        void startsWithNormalizedPrefix() {
            assertTrue(CodeUtil.next("D").startsWith("d"),
                    "前缀应先归一化为小写再拼接");
        }

        @Test
        @DisplayName("前缀后拼接雪花 ID，长度大于前缀长度")
        void appendsSnowflakeId() {
            String id = "dept";
            String out = CodeUtil.next(id);
            assertTrue(out.startsWith(id));
            assertTrue(out.length() > id.length(), "应追加了雪花 ID 后缀，实际=" + out);
        }

        @Test
        @DisplayName("两次生成不重复（雪花 ID 单调递增）")
        void uniqueAcrossCalls() {
            assertNotEquals(CodeUtil.next("d"), CodeUtil.next("d"),
                "自动生成编码若重复会撞唯一约束");
        }
    }
}
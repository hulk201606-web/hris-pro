package com.hris.employee.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MaskUtil 单元测试（EMP-F-025 敏感字段脱敏）。
 *
 * <p>脱敏是PII 防护的最后一道闸：长度边界（证件 ≤6、手机 <7）决定了短号码会不会被
 * "前N后M"切片打穿——这是脱敏真正失效的地方，故每条分支都用边界值而非典型值断言。</p>
 */
class MaskUtilTest {

    @Nested
    @DisplayName("maskCertNo：证件号脱敏（保留前 4 后 2）")
    class CertNo {

        @Test
        @DisplayName("null → null（不返回 \"null\" 字符串）")
        void nullReturnsNull() {
            assertNull(MaskUtil.maskCertNo(null));
        }

        @ParameterizedTest(name = "\"{0}\" → null")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("空串/纯空白 → null")
        void blankReturnsNull(String in) {
            assertNull(MaskUtil.maskCertNo(in));
        }

        @ParameterizedTest(name = "\"{0}\"（长度 {1}）→ 全掩码")
        @CsvSource({"1,1", "12,2", "123456,6"})
        @DisplayName("长度 ≤6 → 全部掩码（不泄露任何位）")
        void shortIdFullyMasked(String in, int len) {
            assertEquals("*".repeat(len), MaskUtil.maskCertNo(in),
                    "短证件号应全掩码，否则前4后2切片会暴露短号全貌");
        }

        @Test
        @DisplayName("边界：长度 8（刚过全掩码阈值）→ 前 4 + 2 星 + 后 2")
        void lengthEightKeepsFourPlusTwo() {
            // len=8 时星数 = min(8, len-6) = 2
            assertEquals("1101" + "**" + "23", MaskUtil.maskCertNo("11010123"));
        }

        @Test
        @DisplayName("长度 9 → 前 4 + 3 星 + 后 2，星数随长度增长")
        void lengthNineHasThreeStars() {
            assertEquals("1101" + "***" + "23", MaskUtil.maskCertNo("110101023"));
        }

        @Test
        @DisplayName("常规 18 位 → 前 4 + 8 星 + 后 2，中间星数封顶 8")
        void longIdCapsStarsAtEight() {
            assertEquals("1101" + "********" + "23", MaskUtil.maskCertNo("11010119900101234523"));
        }

        @Test
        @DisplayName("首尾空白先trim 再脱敏")
        void trimsBeforeMasking() {
            assertEquals(MaskUtil.maskCertNo("11010119900101234523"),
                    MaskUtil.maskCertNo("  11010119900101234523  "));
        }
    }

    @Nested
    @DisplayName("maskMobile：手机号脱敏（保留前 3 后 4）")
    class Mobile {

        @Test
        @DisplayName("null → null")
        void nullReturnsNull() {
            assertNull(MaskUtil.maskMobile(null));
        }

        @ParameterizedTest(name = "\"{0}\" → null")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("空串/纯空白 → null")
        void blankReturnsNull(String in) {
            assertNull(MaskUtil.maskMobile(in));
        }

        @ParameterizedTest(name = "\"{0}\"（长度 {1}）→ 全掩码")
        @CsvSource({"1,1", "123456,6"})
        @DisplayName("长度 <7 → 全部掩码（短号切片会越界，故须全掩）")
        void shortMobileFullyMasked(String in, int len) {
            assertEquals("*".repeat(len), MaskUtil.maskMobile(in),
                    "短手机号应全掩码");
        }

        @Test
        @DisplayName("边界：长度 7（刚过阈值）→ 前 3 + **** + 后 4，无越界且总长 11")
        void lengthSevenKeepsThreePlusFour() {
            // 7 位输入时前 3 与后 4 会重叠，实现取 substring(0,3) + "****" + substring(3)
            assertEquals("138" + "****" + "8888", MaskUtil.maskMobile("1388888"));
        }

        @Test
        @DisplayName("常规 11 位 → 前 3 + **** + 后 4，中间 4 位恒掩码")
        void standardMobileMasked() {
            assertEquals("138" + "****" + "8888", MaskUtil.maskMobile("13812348888"));
        }

        @Test
        @DisplayName("脱敏结果不含明文中间段")
        void neverLeaksMiddleDigits() {
            String masked = MaskUtil.maskMobile("13812348888");
            assertTrue(!masked.contains("1234"), "中间段不应出现在脱敏结果中，实际=" + masked);
        }
    }
}
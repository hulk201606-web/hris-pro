package com.hris.compensation.util;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 换算工具单元测试（CMP-F-023）：DECIMAL 精度 HALF_UP 4 位。
 */
class CompensationMathTest {

    @Test
    @DisplayName("convert：100 USD × 7.2 = 720.0000 CNY")
    void convertShouldMultiply() {
        assertEquals(new BigDecimal("720.0000"),
                CompensationMath.convert(new BigDecimal("100"), new BigDecimal("7.2")));
    }

    @Test
    @DisplayName("convert：HALF_UP 4 位（0.5 舍入位进位）")
    void convertShouldRoundHalfUpAt4() {
        assertEquals(new BigDecimal("1234.5679"),
                CompensationMath.convert(new BigDecimal("1234.56785"), BigDecimal.ONE));
        assertEquals(new BigDecimal("12.3457"),
                CompensationMath.convert(new BigDecimal("1"), new BigDecimal("12.34565")));
    }

    @Test
    @DisplayName("convert：非法输入（null/非正汇率）→ PARAM_INVALID")
    void convertShouldValidateInput() {
        assertThrows(BizException.class, () -> CompensationMath.convert(null, BigDecimal.ONE));
        assertThrows(BizException.class, () -> CompensationMath.convert(BigDecimal.ONE, null));
        BizException e = assertThrows(BizException.class,
                () -> CompensationMath.convert(BigDecimal.ONE, BigDecimal.ZERO));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
    }
}

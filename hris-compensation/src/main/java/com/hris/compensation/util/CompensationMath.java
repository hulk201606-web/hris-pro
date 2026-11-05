package com.hris.compensation.util;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 薪酬数值工具（CMP-F-023 多币种底座）。
 *
 * <p>仅提供纯函数换算工具，F-005 算薪引擎未实现前不改任何算薪链路。</p>
 */
public final class CompensationMath {

    /** 换算结果保留 4 位小数，HALF_UP。 */
    public static final int MONEY_SCALE = 4;

    private CompensationMath() {
    }

    /**
     * 币种换算：amount(源币种) × rate = 目标币种金额，DECIMAL 精度 HALF_UP 保留 4 位。
     *
     * @param amount 源币种金额（非空）
     * @param rate   汇率：1 源币种 = rate 目标币种（非空且 &gt; 0）
     */
    public static BigDecimal convert(BigDecimal amount, BigDecimal rate) {
        if (amount == null || rate == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "金额与汇率不能为空");
        }
        if (rate.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "汇率必须大于 0");
        }
        return amount.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}

package com.hris.compensation.service;

import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorNil;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorNumber;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * round(x, n) 函数：四舍五入到 n 位小数（CMP-F-005 内置函数）。
 *
 * <p>Aviator 内置 math.round(d) 只能取整，不满足薪酬金额保留 2 位小数的需求，
 * 故注册带小数位的 round。空值按 0 处理（薪酬公式容忍空输入，避免单员工失败扩散）。</p>
 */
public class RoundFunction extends AbstractFunction {

    @Override
    public String getName() {
        return "round";
    }

    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject arg1, AviatorObject arg2) {
        Object x = arg1.getValue(env);
        Object n = arg2.getValue(env);
        if (x == null || n == null || x instanceof AviatorNil) {
            return AviatorNumber.valueOf(BigDecimal.ZERO);
        }
        BigDecimal value = x instanceof BigDecimal d ? d : new BigDecimal(x.toString());
        int scale = n instanceof Number num ? num.intValue() : 0;
        return AviatorNumber.valueOf(value.setScale(scale, RoundingMode.HALF_UP));
    }
}

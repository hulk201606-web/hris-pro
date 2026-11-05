package com.hris.compensation.service;

import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorNumber;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * taxMonthly(taxable) 函数：简化月度个税计算（CMP-F-005 内置函数）。
 *
 * <p>入参 taxable = 应纳税所得额（公式侧负责扣减 5000 起征与专项扣除，
 * 见 E2E 口径 taxMonthly(salary+bonus-insurance-5000)），对超出部分按
 * 7 级超额累进税率（3%~45%）计算税额。注意：这是「按月税率表」的简化实现，
 * 非年度累计预扣法（CMP-F-008 的累计预扣依赖跨期间连续账，另批接入）。</p>
 *
 * <p>月度税率表（对 taxable 全额）：
 * 0~3000 3%（速算 0）；3000~12000 10%（速算 210）；12000~25000 20%（速算 1410）；
 * 25000~35000 25%（速算 2660）；35000~55000 30%（速算 4410）；
 * 55000~80000 35%（速算 7160）；80000 以上 45%（速算 15160）。taxable &lt;= 0 税额为 0。</p>
 */
public class TaxMonthlyFunction extends AbstractFunction {

    /** 起征点（公式侧使用，如 salary+bonus-insurance-5000）。 */
    public static final BigDecimal THRESHOLD = new BigDecimal("5000");

    /** 每级：[上限, 税率, 速算扣除数]，上限 null=无上限。 */
    private static final BigDecimal[][] BRACKETS = {
            {new BigDecimal("3000"), new BigDecimal("0.03"), BigDecimal.ZERO},
            {new BigDecimal("12000"), new BigDecimal("0.10"), new BigDecimal("210")},
            {new BigDecimal("25000"), new BigDecimal("0.20"), new BigDecimal("1410")},
            {new BigDecimal("35000"), new BigDecimal("0.25"), new BigDecimal("2660")},
            {new BigDecimal("55000"), new BigDecimal("0.30"), new BigDecimal("4410")},
            {new BigDecimal("80000"), new BigDecimal("0.35"), new BigDecimal("7160")},
            {null, new BigDecimal("0.45"), new BigDecimal("15160")},
    };

    @Override
    public String getName() {
        return "taxMonthly";
    }

    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject arg) {
        Object v = arg.getValue(env);
        BigDecimal taxable = v instanceof BigDecimal d ? d
                : v instanceof Number n ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        if (taxable.signum() <= 0) {
            return AviatorNumber.valueOf(BigDecimal.ZERO);
        }
        // 速算法：税额 = 应纳税所得额 x 税率 - 速算扣除数
        BigDecimal tax = BigDecimal.ZERO;
        for (BigDecimal[] bracket : BRACKETS) {
            BigDecimal upper = bracket[0];
            BigDecimal rate = bracket[1];
            BigDecimal quickDeduct = bracket[2];
            if (upper == null || taxable.compareTo(upper) <= 0) {
                tax = taxable.multiply(rate).subtract(quickDeduct).setScale(2, RoundingMode.HALF_UP);
                break;
            }
        }
        if (tax.signum() < 0) {
            tax = BigDecimal.ZERO;
        }
        return AviatorNumber.valueOf(tax);
    }
}

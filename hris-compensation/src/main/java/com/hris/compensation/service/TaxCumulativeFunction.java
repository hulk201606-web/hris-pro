package com.hris.compensation.service;

import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorNumber;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * taxCumulative(cumTaxable) 函数：年度累计预扣法个税（CMP-F-008，批 8 #18b 内置函数）。
 *
 * <p>入参 cumTaxable = 累计应纳税所得额（公式侧负责扣减：累计收入 - 累计社保公积金 -
 * 累计专项附加扣除 - 60000 基本减除；60000 口径与 taxMonthly 的按月 5000 起征一致，
 * 即公式用法 taxCumulative(累计收入-累计社保-60000)）。对累计应纳税所得额按
 * 综合所得年税率表 7 级超额累进 + 速算扣除计算「累计应纳税额」；实际当期扣缴 =
 * 累计应纳税额 - priorTax（本年已预扣累计，变量缺省 0），由公式侧完成减法。</p>
 *
 * <p>年税率表（对 cumTaxable 全额速算）：
 * 0~36000 3%（速算 0）；36000~144000 10%（速算 2520）；144000~300000 20%（速算 16920）；
 * 300000~420000 25%（速算 31920）；420000~660000 30%（速算 52920）；
 * 660000~960000 35%（速算 85920）；960000 以上 45%（速算 181920）。
 * cumTaxable &lt;= 0 累计应纳税额为 0（公式侧可对当期扣缴再取 max(x,0)）。</p>
 *
 * <p>与 taxMonthly 的关系：单月口径下 taxMonthly(taxable) 与年表口径
 * taxCumulative(taxable*12)/12 在 taxable 处于同一边际档时量级一致（速算扣除同为月表 x12），
 * 单测以「单月对照」校验分级正确性。</p>
 */
public class TaxCumulativeFunction extends AbstractFunction {

    /** 年度基本减除费用（6 万，公式侧使用口径：累计收入-累计社保-60000）。 */
    public static final BigDecimal ANNUAL_ALLOWANCE = new BigDecimal("60000");

    /** 每级：[年累计应纳税所得额上限, 税率, 速算扣除数]，上限 null=无上限。 */
    private static final BigDecimal[][] ANNUAL_BRACKETS = {
            {new BigDecimal("36000"), new BigDecimal("0.03"), BigDecimal.ZERO},
            {new BigDecimal("144000"), new BigDecimal("0.10"), new BigDecimal("2520")},
            {new BigDecimal("300000"), new BigDecimal("0.20"), new BigDecimal("16920")},
            {new BigDecimal("420000"), new BigDecimal("0.25"), new BigDecimal("31920")},
            {new BigDecimal("660000"), new BigDecimal("0.30"), new BigDecimal("52920")},
            {new BigDecimal("960000"), new BigDecimal("0.35"), new BigDecimal("85920")},
            {null, new BigDecimal("0.45"), new BigDecimal("181920")},
    };

    @Override
    public String getName() {
        return "taxCumulative";
    }

    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject arg) {
        Object v = arg.getValue(env);
        BigDecimal taxable = v instanceof BigDecimal d ? d
                : v instanceof Number n ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        if (taxable.signum() <= 0) {
            return AviatorNumber.valueOf(BigDecimal.ZERO);
        }
        // 速算法：累计应纳税额 = 累计应纳税所得额 x 年税率 - 年速算扣除数
        BigDecimal tax = BigDecimal.ZERO;
        for (BigDecimal[] bracket : ANNUAL_BRACKETS) {
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

package com.hris.compensation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * taxCumulative 年度累计预扣函数单测（批 8 #18b）：7 级分级正确 + 与 taxMonthly 单月口径对照。
 */
class TaxCumulativeFunctionTest {

    private final FormulaSandboxService sandbox = new FormulaSandboxService();

    private String eval(String formula) {
        return FormulaSandboxService.toDecimalString(sandbox.evaluate(formula, Map.of()));
    }

    @Test
    @DisplayName("非正数累计应纳税所得额 → 0")
    void nonPositive() {
        assertEquals("0", eval("taxCumulative(0)"));
        assertEquals("0", eval("taxCumulative(-50000)"));
    }

    @Test
    @DisplayName("第一级 3%：36000 → 1080.00")
    void bracket1() {
        assertEquals("1080.00", eval("taxCumulative(36000)"));
    }

    @Test
    @DisplayName("第二级 10%：144000 → 11880.00")
    void bracket2() {
        // 144000*0.10-2520 = 11880
        assertEquals("11880.00", eval("taxCumulative(144000)"));
    }

    @Test
    @DisplayName("第三级 20%：300000 → 43080.00")
    void bracket3() {
        // 300000*0.20-16920 = 43080
        assertEquals("43080.00", eval("taxCumulative(300000)"));
    }

    @Test
    @DisplayName("第四级 25%：420000 → 73080.00")
    void bracket4() {
        // 420000*0.25-31920 = 73080
        assertEquals("73080.00", eval("taxCumulative(420000)"));
    }

    @Test
    @DisplayName("第五级 30%：660000 → 145080.00")
    void bracket5() {
        // 660000*0.30-52920 = 145080
        assertEquals("145080.00", eval("taxCumulative(660000)"));
    }

    @Test
    @DisplayName("第六级 35%：960000 → 250080.00")
    void bracket6() {
        // 960000*0.35-85920 = 250080
        assertEquals("250080.00", eval("taxCumulative(960000)"));
    }

    @Test
    @DisplayName("最高级 45%：1200000 → 358080.00")
    void bracket7() {
        // 1200000*0.45-181920 = 358080
        assertEquals("358080.00", eval("taxCumulative(1200000)"));
    }

    @Test
    @DisplayName("与 taxMonthly 单月口径对照：taxCumulative(12x)/12 == taxMonthly(x)（同边际档）")
    void monthlyConsistency() {
        for (String taxable : new String[]{"3000", "12000", "25000", "35000", "55000", "80000"}) {
            BigDecimal monthly = new BigDecimal(eval("taxMonthly(" + taxable + ")"));
            BigDecimal annualized = new BigDecimal(eval("taxCumulative(" + taxable + "*12)"))
                    .divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
            assertEquals(0, monthly.compareTo(annualized), "taxable=" + taxable);
        }
    }

    @Test
    @DisplayName("公式侧用法：累计应纳税额 - priorTax（已预扣累计，缺省 0）= 当期扣缴")
    void formulaSideUsage() {
        // 公式：taxCumulative(累计收入-累计社保-60000) - priorTax
        // 例：累计收入 240000、累计社保 25200 → 累计应纳税所得额 154800（第三级 20%）
        // 累计应纳税额 = 154800*0.20-16920 = 14040；已预扣 9000 → 当期扣缴 5040
        String r = FormulaSandboxService.toDecimalString(sandbox.evaluate(
                "taxCumulative(cumIncome-cumInsurance-60000)-priorTax",
                Map.of("cumIncome", new BigDecimal("240000"),
                        "cumInsurance", new BigDecimal("25200"),
                        "priorTax", new BigDecimal("9000"))));
        assertEquals("5040.00", r);
    }

    @Test
    @DisplayName("priorTax 缺省 0：公式沙箱白名单变量含 priorTax 且演示环境可 dry-run")
    void priorTaxDefaultZero() {
        var r = sandbox.validate("taxCumulative(base*12-60000)", null);
        // base=10000 → 120000-60000=60000（第二级）：60000*0.10-2520=3480
        assertTrue(r.valid());
        assertEquals("3480.00", r.result());
    }
}

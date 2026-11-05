package com.hris.compensation.service;

import com.hris.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公式沙箱单测（CMP-F-021）：沙箱逃逸拒绝、taxMonthly 分级、内置函数与白名单校验。
 */
class FormulaSandboxServiceTest {

    private final FormulaSandboxService sandbox = new FormulaSandboxService();

    private String eval(String formula, Map<String, Object> env) {
        return FormulaSandboxService.toDecimalString(sandbox.evaluate(formula, env));
    }

    @Nested
    @DisplayName("沙箱逃逸拒绝")
    class Escape {

        @Test
        @DisplayName("new 实例 → 拒绝（ALLOWED_CLASS_SET 空集）")
        void rejectsNewInstance() {
            assertThrows(Exception.class, () -> sandbox.evaluate(
                    "new java.io.File('/tmp/x')", Map.of("base", BigDecimal.ONE)));
        }

        @Test
        @DisplayName("静态方法调用 → 拒绝")
        void rejectsStaticCall() {
            assertThrows(Exception.class, () -> sandbox.evaluate(
                    "System.currentTimeMillis()", Map.of("base", BigDecimal.ONE)));
        }

        @Test
        @DisplayName("静态字段访问 → 拒绝")
        void rejectsStaticField() {
            assertThrows(Exception.class, () -> sandbox.evaluate(
                    "Runtime.getRuntime()", Map.of("base", BigDecimal.ONE)));
        }

        @Test
        @DisplayName("Lambda/循环等高危特性 → 编译拒绝")
        void rejectsLambdaAndLoop() {
            assertThrows(Exception.class, () -> sandbox.evaluate(
                    "(lambda(x) -> x+1)(base)", Map.of("base", BigDecimal.ONE)));
            assertThrows(Exception.class, () -> sandbox.evaluate(
                    "while(base>0){base=base-1}; base", Map.of("base", BigDecimal.ONE)));
        }

        @Test
        @DisplayName("语法错误 → BizException 且带定位信息")
        void syntaxErrorWithPosition() {
            BizException e = assertThrows(BizException.class,
                    () -> sandbox.evaluate("base+*2", Map.of("base", BigDecimal.ONE)));
            assertTrue(e.getMessage().contains("公式语法错误"));
        }
    }

    @Nested
    @DisplayName("taxMonthly 分级（入参=应纳税所得额，公式侧扣 5000 起征）")
    class Tax {

        @Test
        @DisplayName("非正数应纳税所得额 → 0")
        void belowThreshold() {
            assertEquals("0", eval("taxMonthly(0)", Map.of()));
            assertEquals("0", eval("taxMonthly(-100)", Map.of()));
        }

        @Test
        @DisplayName("第一级 3%：taxable=3000 → 90.00")
        void bracket1() {
            assertEquals("90.00", eval("taxMonthly(3000)", Map.of()));
        }

        @Test
        @DisplayName("第二级 10%：taxable=12000 → 990.00")
        void bracket2() {
            // 12000*0.10-210 = 990
            assertEquals("990.00", eval("taxMonthly(12000)", Map.of()));
        }

        @Test
        @DisplayName("第三级 20%：taxable=25000 → 3590.00")
        void bracket3() {
            // 25000*0.20-1410 = 3590
            assertEquals("3590.00", eval("taxMonthly(25000)", Map.of()));
        }

        @Test
        @DisplayName("最高级 45%：taxable=100000 → 29840.00")
        void bracket7() {
            // 100000*0.45-15160 = 29840
            assertEquals("29840.00", eval("taxMonthly(100000)", Map.of()));
        }
    }

    @Nested
    @DisplayName("内置函数与依赖顺序")
    class Functions {

        @Test
        @DisplayName("round(x,n) 四舍五入保留 2 位")
        void round() {
            assertEquals("1050.00", eval("round(10000*0.105,2)", Map.of()));
        }

        @Test
        @DisplayName("min/max 可用")
        void minMax() {
            assertEquals("2", eval("min(2,3)", Map.of()));
            assertEquals("3", eval("max(2,3)", Map.of()));
        }

        @Test
        @DisplayName("前序项结果可被后项引用（依赖顺序）")
        void dependencyOrder() {
            Map<String, Object> env = Map.of("base", new BigDecimal("10000"));
            assertEquals("10000", eval("base", env));
            assertEquals("12000.00", eval("base+round(base*0.2,2)", env));
        }

        @Test
        @DisplayName("未定义变量求值 → 运行期报错（失败隔离的定位来源）")
        void undefinedVariableFails() {
            assertThrows(Exception.class, () -> sandbox.evaluate("noSuchVar+1", Map.of()));
        }
    }

    @Nested
    @DisplayName("validate（F-021）")
    class Validate {

        @Test
        @DisplayName("合法公式 → 通过并返回白名单清单")
        void validFormula() {
            var r = sandbox.validate("round(base*0.105,2)", Map.of("base", new BigDecimal("10000")));
            assertTrue(r.valid());
            assertEquals("1050.00", r.result());
            assertTrue(r.functions().contains("taxMonthly(taxable)"));
            assertTrue(r.baseVariables().contains("base"));
        }

        @Test
        @DisplayName("引用未定义变量 → 拒绝并指出变量名")
        void unknownVariable() {
            var r = sandbox.validate("ghost+1", null);
            assertFalse(r.valid());
            assertTrue(r.message().contains("ghost"));
        }

        @Test
        @DisplayName("缺省样例变量可 dry-run")
        void defaultDemoVars() {
            var r = sandbox.validate("taxMonthly(base-5000)", null);
            assertTrue(r.valid());
            assertEquals("290.00", r.result()); // (10000-5000)*0.10-210
        }
    }
}

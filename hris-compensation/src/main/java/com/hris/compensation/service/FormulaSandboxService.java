package com.hris.compensation.service;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import com.googlecode.aviator.exception.ExpressionSyntaxErrorException;
import com.googlecode.aviator.runtime.function.system.MinFunction;
import com.googlecode.aviator.runtime.function.system.MaxFunction;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 公式沙箱服务（CMP-F-021，基于 Aviator 的白名单表达式求值）。
 *
 * <p>安全边界（沙箱型选型，禁反射/导入）：</p>
 * <ul>
 *   <li>ALLOWED_CLASS_SET = 空集：禁 new 实例与静态方法调用（如 new java.io.File / System.exit）；</li>
 *   <li>FEATURE_SET 排除 Lambda/Module/While/ForLoop：压缩攻击面，只保留表达式求值能力；</li>
 *   <li>变量白名单：base/lateCount/absentDays/priorTax + 前序薪酬项 code，越界变量求值时报错定位；</li>
 *   <li>函数白名单：round/min/max/taxMonthly/taxCumulative，均为注册函数，无反射入口。</li>
 * </ul>
 *
 * <p>数值口径：所有字面量解析为 BigDecimal（ALWAYS_PARSE_*_INTO_DECIMAL），避免浮点误差；
 * 除法走 Aviator 默认 DECIMAL128 数学上下文，最终金额由公式自行 round(x,2) 收敛。</p>
 */
@Service
public class FormulaSandboxService {

    /**
     * 基础变量：定薪基本额 + 考勤扣项（F-010 live 接入）+ priorTax（F-008 累计预扣：
     * 本年已预扣税额累计，缺省 0，跨期间连续账接入后由算薪引擎注入真值）。
     */
    public static final Set<String> BASE_VARIABLES = Set.of("base", "lateCount", "absentDays", "priorTax");

    /** 函数白名单（响应中返回，供前端提示）。 */
    public static final Set<String> FUNCTION_WHITELIST =
            Set.of("round(x,n)", "min(x,y)", "max(x,y)", "taxMonthly(taxable)", "taxCumulative(cumTaxable)");

    /** 独立引擎实例：避免污染 AviatorEvaluator 全局单例的选项与函数表。 */
    private final AviatorEvaluatorInstance engine;

    public FormulaSandboxService() {
        engine = AviatorEvaluator.newInstance();
        // 沙箱：禁一切 new 实例与静态方法调用（空集=全禁）
        engine.setOption(Options.ALLOWED_CLASS_SET, Collections.emptySet());
        // 沙箱：排除高风险语法特性，仅保留 if 等纯表达式能力（三元默认随表达式支持）
        engine.setOption(Options.FEATURE_SET, com.googlecode.aviator.Feature.asSet(
                com.googlecode.aviator.Feature.If));
        // 数值口径：字面量全量解析为 BigDecimal
        engine.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, true);
        engine.setOption(Options.ALWAYS_PARSE_INTEGRAL_NUMBER_INTO_DECIMAL, true);
        // 注册函数白名单
        engine.addFunction(new RoundFunction());
        engine.addFunction(new TaxMonthlyFunction());
        engine.addFunction(new TaxCumulativeFunction());
        engine.addFunction(new MinFunction());
        engine.addFunction(new MaxFunction());
    }

    /**
     * 编译校验：语法错误抛 BizException(PARAM_INVALID)，消息含引擎定位信息。
     */
    public Expression compile(String formula) {
        try {
            return engine.compile(formula, false);
        } catch (ExpressionSyntaxErrorException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公式语法错误: " + e.getMessage());
        }
    }

    /**
     * 求值：变量越界（未在环境中提供）时抛出异常，由调用方（算薪引擎）按员工粒度隔离。
     */
    public Object evaluate(String formula, Map<String, Object> env) {
        Expression exp = compile(formula);
        return exp.execute(env);
    }

    /** 表达式引用的变量集合（供白名单校验）。 */
    public Set<String> variableNames(String formula) {
        return new LinkedHashSet<>(compile(formula).getVariableNames());
    }

    /**
     * F-021 校验入口：编译校验 + 用样例变量 dry-run 求值，返回结果与白名单清单。
     * sampleVars 为空时给一组演示值。
     */
    public ValidateResult validate(String formula, Map<String, Object> sampleVars) {
        Expression exp = compile(formula);
        Set<String> used = new LinkedHashSet<>(exp.getVariableNames());
        Set<String> allowed = new LinkedHashSet<>(BASE_VARIABLES);
        if (sampleVars != null) {
            allowed.addAll(sampleVars.keySet());
        }
        for (String var : used) {
            if (!allowed.contains(var)) {
                return ValidateResult.fail("引用了未定义变量: " + var, used);
            }
        }
        Map<String, Object> env = demoEnv();
        if (sampleVars != null) {
            env.putAll(sampleVars);
        }
        Object result;
        try {
            result = exp.execute(env);
        } catch (RuntimeException e) {
            return ValidateResult.fail("求值失败: " + e.getMessage(), used);
        }
        return new ValidateResult(true, "校验通过", toDecimalString(result), used,
                FUNCTION_WHITELIST, BASE_VARIABLES);
    }

    /** 演示变量集（缺省 dry-run 用；lateCount/absentDays 为 F-010 考勤变量，priorTax 为 F-008 累计预扣变量）。 */
    private Map<String, Object> demoEnv() {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("base", new BigDecimal("10000"));
        env.put("lateCount", 0);
        env.put("absentDays", 0);
        env.put("priorTax", 0);
        return env;
    }

    /** 求值结果统一转 BigDecimal 字符串（布尔/字符串等非数值结果原样转字符串）。 */
    public static String toDecimalString(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal d) {
            return d.toPlainString();
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString()).toPlainString();
        }
        return String.valueOf(v);
    }

    /** 校验结果。 */
    public record ValidateResult(boolean valid, String message, String result,
                                 Set<String> variables, Set<String> functions,
                                 Set<String> baseVariables) {
        static ValidateResult fail(String message, Set<String> used) {
            return new ValidateResult(false, message, null, used, FUNCTION_WHITELIST, BASE_VARIABLES);
        }
    }
}

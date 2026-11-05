package com.hris.compensation.service;

import com.hris.compensation.entity.PayItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 薪酬项求值器（CMP-F-005 核心，供整批计算与单人重算复用）。
 *
 * <p>求值语义：变量集 = 定薪基本额（base，来自 cmp_employee_pay）
 * + 考勤扣项占位变量（lateCount/absentDays，F-010 考勤联动另批接入，本批固定 0）
 * + 已求值前序项（按 seq 顺序，后项可引用前项 code）。
 * NET 项公式缺省 = sum(EARNING)-sum(DEDUCTION)-sum(TAX)。</p>
 */
@Component
public class PayItemEvaluator {

    /** 考勤扣项占位变量（F-010 另批接入，本批固定 0）。 */
    private static final BigDecimal PLACEHOLDER_ZERO = BigDecimal.ZERO;

    private final FormulaSandboxService sandbox;

    public PayItemEvaluator(FormulaSandboxService sandbox) {
        this.sandbox = sandbox;
    }

    /**
     * 按 seq 顺序逐项求值。
     *
     * @return 结果快照：base / 各项 code（Decimal 字符串）/ periodCode
     */
    public Map<String, Object> evalItems(List<PayItem> items, BigDecimal base, String periodCode) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("base", base);
        env.put("lateCount", PLACEHOLDER_ZERO);
        env.put("absentDays", PLACEHOLDER_ZERO);
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("base", base.toPlainString());
        for (PayItem item : items) {
            Object value = evalItem(item, items, env);
            env.put(item.getCode(), value);
            results.put(item.getCode(), FormulaSandboxService.toDecimalString(value));
        }
        results.put("periodCode", periodCode);
        return results;
    }

    private Object evalItem(PayItem item, List<PayItem> all, Map<String, Object> env) {
        if (PayItem.CATEGORY_NET.equals(item.getCategory()) && isBlank(item.getFormula())) {
            return defaultNet(all, env);
        }
        if (isBlank(item.getFormula())) {
            // 无公式的非 NET 项按 0 计（固定项录入走调整通道）
            return BigDecimal.ZERO;
        }
        return sandbox.evaluate(item.getFormula(), env);
    }

    /** NET 缺省公式：sum(EARNING)-sum(DEDUCTION)-sum(TAX)。 */
    private Object defaultNet(List<PayItem> all, Map<String, Object> env) {
        BigDecimal net = BigDecimal.ZERO;
        for (PayItem item : all) {
            Object v = env.get(item.getCode());
            if (v == null) {
                continue;
            }
            BigDecimal d = new BigDecimal(FormulaSandboxService.toDecimalString(v));
            switch (item.getCategory()) {
                case PayItem.CATEGORY_EARNING -> net = net.add(d);
                case PayItem.CATEGORY_DEDUCTION, PayItem.CATEGORY_TAX -> net = net.subtract(d);
                default -> { /* NET 不参与缺省求和 */ }
            }
        }
        return net;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

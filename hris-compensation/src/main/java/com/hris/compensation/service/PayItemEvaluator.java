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
 * + 考勤扣项变量（lateCount/absentDays，F-010 live 接入：求值前由算薪引擎经
 * att attendance-brief 批量拉取，att 不可用时降级 0 并标注 fallback）
 * + 累计预扣变量（priorTax，F-008 年度累计预扣：本年已预扣税额累计，缺省 0）
 * + 已求值前序项（按 seq 顺序，后项可引用前项 code）。
 * NET 项公式缺省 = sum(EARNING)-sum(DEDUCTION)-sum(TAX)。</p>
 */
@Component
public class PayItemEvaluator {

    private final FormulaSandboxService sandbox;

    public PayItemEvaluator(FormulaSandboxService sandbox) {
        this.sandbox = sandbox;
    }

    /**
     * 按 seq 顺序逐项求值。
     *
     * @param lateCount  周期内迟到次数（F-010，att 不可用时传 0）
     * @param absentDays 周期内缺勤天数（F-010，att 不可用时传 0）
     * @return 结果快照：base / 各项 code（Decimal 字符串）/ periodCode
     */
    public Map<String, Object> evalItems(List<PayItem> items, BigDecimal base, String periodCode,
                                         long lateCount, long absentDays) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("base", base);
        env.put("lateCount", BigDecimal.valueOf(lateCount));
        env.put("absentDays", BigDecimal.valueOf(absentDays));
        env.put("priorTax", BigDecimal.ZERO);
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

package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.compensation.entity.EmployeePay;
import com.hris.compensation.entity.PayItem;
import com.hris.compensation.mapper.EmployeePayMapper;
import com.hris.compensation.mapper.PayItemMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 算薪引擎的取数与校验工具（CMP-F-005/006）：薪资项装载、现行有效定薪装载与金额解析。
 *
 * <p>从 PayrollCalcService 拆分而来（单文件 ≤300 行门禁），mapper 由调用方传入，
 * 因此宿主构造器签名保持不变。</p>
 */
final class PayrollCalcSupport {

    private PayrollCalcSupport() {
    }

    /** 薪资组启用项（含全局 0=全局项），seq 升序；NET 缺失时自动补缺省项。 */
    static List<PayItem> loadItems(PayItemMapper payItemMapper, Long payGroupId) {
        List<PayItem> items = payItemMapper.selectList(new LambdaQueryWrapper<PayItem>()
                .in(PayItem::getPayGroupId, List.of(payGroupId, 0L))
                .eq(PayItem::getStatus, 1)
                .orderByAsc(PayItem::getSeq).orderByAsc(PayItem::getId));
        boolean hasNet = items.stream().anyMatch(i -> PayItem.CATEGORY_NET.equals(i.getCategory()));
        if (!hasNet) {
            PayItem net = new PayItem();
            net.setCode("netPay");
            net.setName("实发(缺省)");
            net.setCategory(PayItem.CATEGORY_NET);
            net.setSeq(Integer.MAX_VALUE);
            items = new ArrayList<>(items);
            items.add(net);
        }
        return items;
    }

    /** 现行有效定薪（effective_from <= 期间月末 且 effective_to 为空或 >= 期间月初）。 */
    static List<EmployeePay> loadEffectiveEmpPays(EmployeePayMapper employeePayMapper, Long payGroupId, String periodCode) {
        YearMonth ym = YearMonth.parse(periodCode);
        LocalDate periodStart = ym.atDay(1);
        LocalDate periodEnd = ym.atEndOfMonth();
        return employeePayMapper.selectList(new LambdaQueryWrapper<EmployeePay>()
                .eq(EmployeePay::getPayGroupId, payGroupId)
                .le(EmployeePay::getEffectiveFrom, periodEnd)
                .and(w -> w.isNull(EmployeePay::getEffectiveTo)
                        .or().ge(EmployeePay::getEffectiveTo, periodStart)));
    }

    static EmployeePay requireEffectiveEmpPay(EmployeePayMapper employeePayMapper, Long payGroupId,
                                              String periodCode, Long empId) {
        List<EmployeePay> pays = loadEffectiveEmpPays(employeePayMapper, payGroupId, periodCode).stream()
                .filter(p -> p.getEmpId().equals(empId)).toList();
        if (pays.isEmpty()) {
            throw new BizException(ErrorCode.CMP_ERROR, "员工 " + empId + " 无现行有效定薪");
        }
        return pays.get(0);
    }

    /** 解析定薪金额（密域列当前以明文数值存于 cipher 列），非法即按算薪错误抛出。 */
    static BigDecimal parseAmount(String cipher, String errMsg) {
        if (cipher == null || cipher.isBlank()) {
            throw new BizException(ErrorCode.CMP_ERROR, errMsg);
        }
        try {
            return new BigDecimal(cipher.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.CMP_ERROR, errMsg);
        }
    }

    /** 任务行错误信息截断到 255 字符（DDL 列宽）。 */
    static String truncate(String msg) {
        if (msg == null || msg.isBlank()) {
            return "算薪失败";
        }
        return msg.length() > 255 ? msg.substring(0, 255) : msg;
    }
}

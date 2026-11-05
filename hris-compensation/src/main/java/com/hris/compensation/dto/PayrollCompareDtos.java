package com.hris.compensation.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 算薪环比对比 DTO（CMP-F-013a，试算差异对比）。
 *
 * <p>口径：base=上期批次明细，current=当前批次明细；diff=current-base。
 * 逐项金额取自 cmp_payroll_detail.input_snapshot.items（业务层明文，密文列已由
 * CipherTypeHandler 读时解密）；changeRate=netDiff/baseNet，baseNet 为 0 或缺失时为 null。</p>
 */
public final class PayrollCompareDtos {

    private PayrollCompareDtos() {
    }

    /** 单项差异（code=薪酬项编码）。 */
    public record ItemDiff(String code, BigDecimal base, BigDecimal current, BigDecimal diff) {
    }

    /** 单员工差异（grossDiff=应发差，netDiff=实发差）。 */
    public record EmployeeCompare(Long empId, List<ItemDiff> items, BigDecimal grossDiff,
                                  BigDecimal netDiff, BigDecimal changeRate) {
    }

    /**
     * 批次对比响应。baseRunId 为 null 表示同期间无上期批次（employees 为空，note 说明）。
     */
    public record PayrollCompareResponse(Long runId, Long baseRunId, String periodCode,
                                         String basePeriodCode, int employeeCount,
                                         BigDecimal grossDiff, BigDecimal netDiff, BigDecimal changeRate,
                                         List<EmployeeCompare> employees, String note) {
    }
}

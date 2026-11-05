package com.hris.compensation.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.compensation.dto.ReportDtos.LegalEntityCostRow;
import com.hris.compensation.service.LegalEntityCostReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 报表端点（CMP-F-017 多法人成本合并报表，MVP 单库聚合口径）。
 *
 * <p>权限口径：复用 payroll read 权限码（cmp:payroll-detail:read）。本端点只返回
 * 法人级汇总总额，不返回单人明细、密文或掩码，因此不要求 cmp:salary:read 密域权限；
 * 但持有者可看到法人级成本总量，权限分配时须按报表可见范围评估。</p>
 */
@RestController
@RequestMapping("/api/v1/cmp/reports")
public class ReportController {

    private final LegalEntityCostReportService service;

    public ReportController(LegalEntityCostReportService service) {
        this.service = service;
    }

    /**
     * 多法人成本合并报表：按周期返回各法人 {headcount, grossTotal, deductionTotal, taxTotal, netTotal}。
     * deductionTotal = 应发-实发（社保+个税+其他扣减合计），taxTotal 恒为 null（MVP 无逐人个税数据源）。
     */
    @RequirePerm("cmp:payroll-detail:read")
    @GetMapping("/legal-entity-cost")
    public R<List<LegalEntityCostRow>> legalEntityCost(@RequestParam String periodCode) {
        return R.ok(service.legalEntityCost(periodCode));
    }
}

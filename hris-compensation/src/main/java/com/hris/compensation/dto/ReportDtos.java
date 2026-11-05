package com.hris.compensation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 报表相关 DTO（CMP-F-017 多法人成本合并报表，MVP 单库聚合口径）。
 *
 * <p>口径说明：本组 DTO 只承载法人级汇总，不携带单人明细与密文；
 * 密域权限（cmp:salary:read）不作为报表端点门槛——端点挂 payroll read 权限码，
 * 因为其输出为聚合总额，无法还原任何单人金额。</p>
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** 法人简报批量请求（org 内部端点 POST /internal/org/legal-entities/brief）。 */
    public record LegalEntityBriefRequest(List<Long> ids) {
    }

    /** 法人简报项：id + name，供报表名称富化。 */
    public record LegalEntityBriefItem(Long id, String name) {
    }

    /**
     * 法人维度成本合并行。
     *
     * <p>deductionTotal = 应发 - 实发（社保公积金+个税+其他扣减合计，MVP 无法拆分）；
     * taxTotal 恒为 null：cmp_payroll_detail 无逐人个税落地列，待算薪引擎输出个税后补齐。</p>
     */
    public record LegalEntityCostRow(Long legalEntityId, String legalEntityName, long headcount,
                                     BigDecimal grossTotal, BigDecimal deductionTotal,
                                     BigDecimal taxTotal, BigDecimal netTotal) {
    }

    /** 员工雇佣简报批量请求（emp 内部端点 POST /internal/emp/headcount-structure-brief）。 */
    public record HeadcountStructureBriefRequest(LocalDate asOf) {
    }

    /**
     * 员工雇佣简报行：emp_employee × emp_employment（现行或 asOf 时点）批量简报。
     * status 为 emp_employee.status 原始码（0 待入职/1 试用/2 在职/3 离职中/4 已离职…）。
     */
    public record EmploymentBriefRow(Long empId, Long legalEntityId, Long deptId,
                                     String hireType, Integer status) {
    }
}

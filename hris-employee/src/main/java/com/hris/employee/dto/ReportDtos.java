package com.hris.employee.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表相关 DTO（P1 人力结构分析报表数据源：员工×雇佣关系批量简报）。
 *
 * <p>只承载组织维度字段（法人/部门/雇佣类型/在职状态码），不携带姓名/证件等敏感信息，
 * 供 org 侧纯聚合使用；legalEntityId 供 CMP-F-017 多法人成本报表做 empId→法人映射。</p>
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** 人力结构简报批量请求：asOf 为空取现行雇佣关系；指定日期取该时点有效版本。 */
    public record HeadcountStructureBriefRequest(LocalDate asOf) {
    }

    /**
     * 人力结构简报行。status 为 emp_employee.status 原始码
     * （0 待入职/1 试用/2 在职/3 离职中/4 已离职/5 已取消/6 退休/7 黑名单）。
     */
    public record HeadcountStructureBriefRow(Long empId, Long legalEntityId, Long deptId,
                                             String hireType, Integer status) {
    }
}

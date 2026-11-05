package com.hris.org.dto;

import java.util.List;

/**
 * 报表相关 DTO（CMP-F-017 法人简报富化 + P1 人力结构分析报表）。
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** 法人简报批量请求（内部端点，按 id 集合查询）。 */
    public record LegalEntityBriefRequest(List<Long> ids) {
    }

    /** 法人简报项：id + name，供薪酬域报表名称富化。 */
    public record LegalEntityBriefItem(Long id, String name) {
    }

    /** 员工雇佣简报批量请求（emp 内部端点透传载荷；asOf 为空取现行雇佣关系）。 */
    public record HeadcountStructureBriefRequest(java.time.LocalDate asOf) {
    }

    /** 员工雇佣简报行：emp_employee × emp_employment（现行或 asOf 时点）。 */
    public record HeadcountStructureBriefRow(Long empId, Long legalEntityId, Long deptId,
                                             String hireType, Integer status) {
    }

    /** 人力结构分析报表响应：按部门/雇佣类型/在职状态三个维度的人数聚合。 */
    public record HeadcountStructureResponse(List<DeptCount> byDept,
                                             List<HireTypeCount> byHireType,
                                             List<StatusCount> byStatus) {
    }

    /** 部门维度计数项。deptScope=top 时为一级部门滚动聚合（含下辖全部层级）。 */
    public record DeptCount(Long deptId, String deptName, long count) {
    }

    /** 雇佣类型维度计数项（FULLTIME/PARTTIME/INTERN/CONSULTANT）。 */
    public record HireTypeCount(String hireType, long count) {
    }

    /** 在职状态维度计数项（emp_employee.status 码：1 试用/2 在职/3 离职中/4 已离职…）。 */
    public record StatusCount(Integer status, long count) {
    }
}

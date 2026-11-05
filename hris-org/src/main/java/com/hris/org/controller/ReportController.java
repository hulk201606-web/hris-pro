package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.org.dto.ReportDtos.HeadcountStructureResponse;
import com.hris.org.service.HeadcountStructureReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报表端点（P1 人力结构分析报表，纯聚合）。
 */
@RestController
@RequestMapping("/api/v1/org/reports")
public class ReportController {

    private final HeadcountStructureReportService service;

    public ReportController(HeadcountStructureReportService service) {
        this.service = service;
    }

    /**
     * 人力结构分析：按部门（deptScope=top 一级部门滚动聚合，默认；all 逐部门）、
     * 雇佣类型（hire_type）、在职状态（emp_employee.status 码）三个维度返回人数聚合。
     * 复用 org read 权限码（org:dept:read）：仅输出聚合计数，不暴露员工个体信息。
     */
    @RequirePerm("org:dept:read")
    @GetMapping("/headcount-structure")
    public R<HeadcountStructureResponse> headcountStructure(
            @RequestParam(defaultValue = "top") String deptScope) {
        return R.ok(service.structure(deptScope));
    }
}

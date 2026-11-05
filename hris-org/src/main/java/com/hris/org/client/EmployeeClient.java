package com.hris.org.client;

import com.hris.common.api.R;
import com.hris.org.dto.ReportDtos.HeadcountStructureBriefRequest;
import com.hris.org.dto.ReportDtos.HeadcountStructureBriefRow;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 员工域 Feign 客户端（用于汇报线解析 docs/07：GET /internal/emp/{id}/profile-brief）。
 *
 * <p>返回 hris-employee 的统一响应体 R；调用方需解包 getData()。租户上下文经 X-Tenant-Id 头透传（见 OrgFeignConfig）。</p>
 */
@FeignClient(name = "hris-employee", path = "/internal/emp")
public interface EmployeeClient {

    /**
     * 员工档案摘要（含 deptId / supervisorEmpId 等）。
     */
    @GetMapping("/{id}/profile-brief")
    R<Map<String, Object>> profileBrief(@PathVariable("id") Long empId);

    /**
     * 按部门统计在职员工数（ORG-F-018 级联校验：部门删除前调用，有在职员工须拒绝）。
     */
    @GetMapping("/count-active-by-dept")
    R<Long> countActiveByDept(@RequestParam("deptId") Long deptId);

    /**
     * 员工雇佣简报批量查询（P1 人力结构分析报表数据源；asOf 为空取现行雇佣关系）。
     * 调用方 fail-open（见 HeadcountStructureReportService）。
     */
    @PostMapping("/headcount-structure-brief")
    R<List<HeadcountStructureBriefRow>> headcountStructureBrief(@RequestBody HeadcountStructureBriefRequest req);
}

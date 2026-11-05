package com.hris.compensation.client;

import com.hris.common.api.R;
import com.hris.compensation.dto.ReportDtos.EmploymentBriefRow;
import com.hris.compensation.dto.ReportDtos.HeadcountStructureBriefRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 员工域 Feign 客户端（docs/07 内部契约：档案简报）。
 *
 * <p>返回 hris-employee 的统一响应体 R；租户上下文经 X-Tenant-Id 头透传（见 CompensationFeignConfig）。</p>
 */
@FeignClient(name = "hris-employee", path = "/internal/emp")
public interface EmployeeClient {

    /**
     * 档案简报：算薪取数读取姓名/工号/部门/汇报线等。
     */
    @GetMapping("/{id}/profile-brief")
    R<Map<String, Object>> profileBrief(@PathVariable("id") Long empId);

    /**
     * 员工雇佣简报批量查询（CMP-F-017 报表：empId→法人映射，MVP 用现行雇佣关系）。
     */
    @PostMapping("/headcount-structure-brief")
    R<List<EmploymentBriefRow>> headcountStructureBrief(@RequestBody HeadcountStructureBriefRequest req);
}

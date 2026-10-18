package com.hris.performance.client;

import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 员工域 Feign 客户端（docs/07 内部契约：档案简报）。
 *
 * <p>绩效上下文取数读取员工姓名/部门/汇报线；租户上下文经 X-Tenant-Id 头透传（见 PerformanceFeignConfig）。</p>
 */
@FeignClient(name = "hris-employee", path = "/internal/emp")
public interface EmployeeClient {

    /** 档案简报：按员工 ID 取姓名/工号/部门/汇报线等。 */
    @GetMapping("/{id}/profile-brief")
    R<Map<String, Object>> profileBrief(@PathVariable("id") Long empId);
}

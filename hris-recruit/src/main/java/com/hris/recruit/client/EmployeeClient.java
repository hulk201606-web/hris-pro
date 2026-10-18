package com.hris.recruit.client;

import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 员工域 Feign 客户端（docs/07 内部契约：候选人预建档 / 档案简报）。
 *
 * <p>录用建档调用 /internal/emp/pre-create 预创建员工主档（状态=待入职）；
 * pre-create 入参以 Map 透传，与员工域 PreCreateRequest 字段对齐（name/mobile/email/empNoPrefix 等）。
 * 租户上下文经 X-Tenant-Id 头透传（见 RecruitFeignConfig）。</p>
 */
@FeignClient(name = "hris-employee", path = "/internal/emp")
public interface EmployeeClient {

    /** 候选人预建档：返回新建员工 ID。 */
    @PostMapping("/pre-create")
    R<Long> preCreate(@RequestBody Map<String, Object> req);

    /** 档案简报：按员工 ID 取姓名/工号/部门等。 */
    @GetMapping("/{id}/profile-brief")
    R<Map<String, Object>> profileBrief(@PathVariable("id") Long empId);
}

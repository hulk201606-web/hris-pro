package com.hris.recruit.client;

import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 员工域黑名单校验 Feign 客户端（RCS-F-011，与 EmployeeClient/FlowClient 同款范式）。
 *
 * <p>契约（EMP 域并行作业提供）：GET /internal/emp/blacklist-check，
 * 参数 certNoHash（证件号哈希，无则可空）+ mobile（手机号），
 * 返回 data = {blacklisted: Boolean, reason: String}。
 * 租户上下文经 X-Tenant-Id 头透传（见 RecruitFeignConfig）。</p>
 *
 * <p>fail-open 语义在 {@code BlacklistGuard} 收敛：emp 不可用时 log.warn 放行，
 * 可用性优先于黑名单拦截（招聘漏斗数据完整性优先，风险由人工复核兜底）。</p>
 */
@FeignClient(name = "hris-employee", path = "/internal/emp")
public interface EmpCheckClient {

    /** 黑名单校验：任一维度命中即返回 blacklisted=true。 */
    @GetMapping("/blacklist-check")
    R<Map<String, Object>> blacklistCheck(@RequestParam("certNoHash") String certNoHash,
                                          @RequestParam("mobile") String mobile);
}

package com.hris.employee.client;

import com.hris.common.api.R;
import com.hris.employee.dto.HcCheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 组织域 Feign 客户端（docs/07 内部契约：编制校验 / 汇报线）。
 *
 * <p>返回 hris-org 的统一响应体 R；租户上下文经 X-Tenant-Id 头透传（见 EmployeeFeignConfig）。</p>
 */
@FeignClient(name = "hris-org", path = "/internal/org")
public interface OrgClient {

    /**
     * 编制校验：入职/调动时调用，编制不足返回 shortfall > 0。
     */
    @GetMapping("/hc/check")
    R<HcCheckResponse> check(@RequestParam("dept") Long dept,
                             @RequestParam("position") Long position,
                             @RequestParam("count") Integer count);
}

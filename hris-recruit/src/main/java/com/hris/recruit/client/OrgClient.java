package com.hris.recruit.client;

import com.hris.common.api.R;
import com.hris.recruit.dto.HcCheckDtos.HcCheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 组织域 Feign 客户端（RCS 需求提交编制校验）。
 *
 * <p>契约：GET /internal/org/hc/check?dept&position&count，返回 allowed/quota/occupied/shortfall。
 * 租户上下文经 X-Tenant-Id 头透传（见 RecruitFeignConfig）。</p>
 */
@FeignClient(name = "hris-org", path = "/internal/org", contextId = "recruitOrgClient")
public interface OrgClient {

    @GetMapping("/hc/check")
    R<HcCheckResponse> check(@RequestParam("dept") Long dept,
                             @RequestParam("position") Long position,
                             @RequestParam("count") Integer count);
}

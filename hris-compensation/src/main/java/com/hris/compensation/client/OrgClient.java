package com.hris.compensation.client;

import com.hris.common.api.R;
import com.hris.compensation.dto.ReportDtos.LegalEntityBriefItem;
import com.hris.compensation.dto.ReportDtos.LegalEntityBriefRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 组织域 Feign 客户端（CMP-F-017 多法人成本合并报表：法人名称富化）。
 *
 * <p>返回 hris-org 的统一响应体 R；租户上下文经 X-Tenant-Id 头透传（见 CompensationFeignConfig）。
 * 调用方 fail-open：org 不可用时法人名称返回 null，不阻断报表主流程。</p>
 */
@FeignClient(name = "hris-org", path = "/internal/org")
public interface OrgClient {

    /**
     * 法人简报批量查询：按 id 集合返回 {id, name}，供报表名称富化。
     */
    @PostMapping("/legal-entities/brief")
    R<List<LegalEntityBriefItem>> legalEntityBrief(@RequestBody LegalEntityBriefRequest req);
}

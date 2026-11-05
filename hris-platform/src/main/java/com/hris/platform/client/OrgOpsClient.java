package com.hris.platform.client;

import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 组织域运维操作内部客户端（platform 调度中心跨域任务用）。
 *
 * <p>调度线程无 Authorization 头，租户上下文由 SchedulerRegistry 注入后经
 * traceFeignInterceptor 透传 X-Tenant-Id，下游 TraceIdFilter 还原。</p>
 */
@FeignClient(name = "hris-org", contextId = "orgOpsClient", path = "/internal/org")
public interface OrgOpsClient {

    /**
     * 组织版本到期激活（批 11 #23）：按 effective_from &lt;= effectiveDate 的
     * DRAFT 批次升序激活，最后一批成为 ACTIVE。
     *
     * @param tenantId      租户（调度注册项租户）
     * @param effectiveDate 生效截止日（ISO yyyy-MM-dd，null = 今天）
     * @return 激活结果（含 activated 计数）
     */
    @PostMapping("/versions/activate-due")
    R<Map<String, Object>> activateDueVersions(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam(value = "effectiveDate", required = false) String effectiveDate);
}

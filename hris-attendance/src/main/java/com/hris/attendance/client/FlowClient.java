package com.hris.attendance.client;

import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 流程中台 Feign 客户端（ATT_LEAVE / ATT_OT / ATT_TRIP / ATT_FIX 审批发起）。
 *
 * <p>返回 hris-flow 的统一响应体 R；调用方需解包 getData()（与 org/employee 域 FlowClient 约定一致）。
 * 租户上下文经 X-Tenant-Id 头透传（见 AttendanceFeignConfig）。</p>
 */
@FeignClient(name = "hris-flow", path = "/internal/flow")
public interface FlowClient {

    /**
     * 发起流程实例。request 含 bizType / bizKey / payload（tenantId 以透传头为准）。
     * data 含 instanceId / bizType / bizKey / status。
     */
    @PostMapping("/instances")
    R<Map<String, Object>> startInstance(@RequestBody Map<String, Object> request);
}

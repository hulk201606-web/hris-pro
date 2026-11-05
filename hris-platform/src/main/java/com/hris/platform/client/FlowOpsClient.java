package com.hris.platform.client;

import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/**
 * 流程域运维操作内部客户端（platform 调度中心跨域任务用，docs/19 §10.5）。
 *
 * <p>调度线程无 Authorization 头，租户上下文由 SchedulerRegistry 注入后经
 * traceFeignInterceptor 透传 X-Tenant-Id，下游 TraceIdFilter 还原。</p>
 */
@FeignClient(name = "hris-flow", contextId = "flowOpsClient", path = "/internal/flow")
public interface FlowOpsClient {

    /**
     * 超时任务升级改派（docs/19 §10.5）：扫描 overdue 且配置了 escalateAfterH 的任务，
     * 按 escalate_to 执行升级改派并留痕 ESCALATE（幂等）。
     *
     * @return scanned/escalated/skippedIdempotent 计数
     */
    @PostMapping("/tasks/escalate-overdue")
    R<Map<String, Integer>> escalateOverdue();
}

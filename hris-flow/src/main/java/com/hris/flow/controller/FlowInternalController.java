package com.hris.flow.controller;

import com.hris.common.api.R;
import com.hris.flow.dto.StartInstanceRequest;
import com.hris.flow.dto.StartInstanceResponse;
import com.hris.flow.service.EventOutboxService;
import com.hris.flow.service.InstanceService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 流程中台内部端点（网关封禁外部访问，Feign 双向签名）。
 *
 * <p>对齐 hris-employee / hris-org 的 {@code FlowClient} 预留契约
 * （POST /internal/flow/instances，body: bizType/bizKey/tenantId/payload）。
 * body 中 tenantId 不信任，以网关透传的 X-Tenant-Id（TraceContext）为准。</p>
 */
@RestController
@RequestMapping("/internal/flow")
public class FlowInternalController {

    private final InstanceService instanceService;
    private final EventOutboxService eventOutboxService;

    public FlowInternalController(InstanceService instanceService, EventOutboxService eventOutboxService) {
        this.instanceService = instanceService;
        this.eventOutboxService = eventOutboxService;
    }

    @PostMapping("/instances")
    public R<StartInstanceResponse> start(@RequestBody Map<String, Object> body) {
        StartInstanceRequest req = new StartInstanceRequest(
                str(body.get("bizType")),
                str(body.get("bizKey")),
                str(body.get("procDefKey")),
                map(body.get("payload")),
                map(body.get("variables")),
                longOrNull(body.get("initiator")),
                longOrNull(body.get("assignee")));
        return R.ok(instanceService.start(req));
    }

    /**
     * outbox 重试入口（docs/19 §8.7-1）：遍历 PENDING 审批回调事件按 bizType 路由重试，
     * 成功置 SENT、失败保留 PENDING。供手动/外部调度触发，不做定时器。
     */
    @PostMapping("/outbox/dispatch")
    public R<Map<String, Integer>> dispatchOutbox(@RequestParam(required = false, defaultValue = "100") int limit) {
        return R.ok(eventOutboxService.dispatchPending(limit));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Long longOrNull(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }
}

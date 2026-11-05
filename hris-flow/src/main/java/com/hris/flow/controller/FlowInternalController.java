package com.hris.flow.controller;

import com.hris.common.api.R;
import com.hris.flow.dto.StartInstanceRequest;
import com.hris.flow.dto.StartInstanceResponse;
import com.hris.flow.service.EventOutboxService;
import com.hris.flow.service.InstanceService;
import com.hris.flow.service.TaskMaintenanceService;
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
    private final TaskMaintenanceService taskMaintenanceService;

    public FlowInternalController(InstanceService instanceService, EventOutboxService eventOutboxService,
                                  TaskMaintenanceService taskMaintenanceService) {
        this.instanceService = instanceService;
        this.eventOutboxService = eventOutboxService;
        this.taskMaintenanceService = taskMaintenanceService;
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
                longOrNull(body.get("assignee")),
                longList(body.get("ccEmpIds")));
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

    /**
     * 超时升级（FLW-F-010，docs/19 §10.5-C）：扫描本租户运行中任务，按 flow_ext_timeout_cfg
     * 的 escalateAfterH/escalate_to 执行升级改派并留痕 ESCALATE；幂等（已升级任务按
     * task_action 判断不重复）。供 platform 定时调度触发。
     *
     * @return scanned/escalated/skippedIdempotent 计数
     */
    @PostMapping("/tasks/escalate-overdue")
    public R<Map<String, Integer>> escalateOverdue() {
        return R.ok(taskMaintenanceService.escalateOverdue());
    }

    /**
     * 待办随负责人重算（docs/19 §10.5-A2）：body {oldEmpId, newEmpId}——本租户所有运行中
     * 实例里 assignee=oldEmpId 的任务改为 newEmpId 并留痕 ASSIGN。幂等：重复调用结果一致，
     * 无 oldEmpId 任务时 reassigned=0。org 负责人变更时由 org 侧调用。
     */
    @PostMapping("/tasks/reassign-manager")
    public R<Map<String, Integer>> reassignManager(@RequestBody Map<String, Object> body) {
        return R.ok(taskMaintenanceService.reassignManager(
                longOrNull(body.get("oldEmpId")), longOrNull(body.get("newEmpId"))));
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

    private java.util.List<Long> longList(Object v) {
        if (!(v instanceof java.util.List<?> list)) {
            return null;
        }
        return list.stream()
                .filter(x -> x instanceof Number)
                .map(x -> ((Number) x).longValue())
                .toList();
    }
}

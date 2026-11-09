package com.hris.flow.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.TaskActionRequest;
import com.hris.flow.entity.BizRelation;
import com.hris.flow.entity.TaskAction;
import com.hris.flow.enums.InstanceStatus;
import com.hris.flow.enums.TaskActionType;
import com.hris.flow.mapper.TaskActionMapper;
import com.hris.flow.util.JsonUtil;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * TaskActionService 的动作支撑（FLW-F-007/008/009）：任务校验、动作留痕、实例终态回写与回调。
 *
 * <p>从 TaskActionService 拆分而来（单文件 ≤300 行门禁）。持有宿主所需的协作依赖，
 * 由宿主构造器内实例化，因此 TaskActionService 对外构造器签名（11 参）保持不变。</p>
 */
final class TaskActionSupport {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final TaskActionMapper taskActionMapper;
    private final BizRelationService bizRelationService;
    private final EventOutboxService eventOutboxService;
    private final ApprovalCallbackDispatcher callbackDispatcher;
    private final DelegateService delegateService;

    TaskActionSupport(TaskService taskService, RuntimeService runtimeService,
                      HistoryService historyService, TaskActionMapper taskActionMapper,
                      BizRelationService bizRelationService, EventOutboxService eventOutboxService,
                      ApprovalCallbackDispatcher callbackDispatcher, DelegateService delegateService) {
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.taskActionMapper = taskActionMapper;
        this.bizRelationService = bizRelationService;
        this.eventOutboxService = eventOutboxService;
        this.callbackDispatcher = callbackDispatcher;
        this.delegateService = delegateService;
    }

    /** 留痕备注：在审批意见前注明动作语义。 */
    TaskActionRequest withNote(TaskActionRequest req, String note) {
        String origin = req == null || req.comment() == null || req.comment().isBlank()
                ? null : req.comment();
        String comment = origin == null ? note : note + "：" + origin;
        return new TaskActionRequest(comment,
                req == null ? null : req.attachments(),
                req == null ? null : req.targetUserId(),
                req == null ? null : req.terminate(),
                req == null ? null : req.formValues(),
                req == null ? null : req.tag());
    }

    /** 当前活动节点 ID（运行中实例）。 */
    String currentActivityId(String procInstId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).singleResult();
        if (pi == null || pi.getActivityId() == null) {
            throw new BizException(ErrorCode.FLOW_STATE_CONFLICT, "流程实例不在运行中，无法退回");
        }
        return pi.getActivityId();
    }

    /** 起始用户任务：实例历史中最早执行的 userTask（兼容 start 后经网关/分支路由的模板）。 */
    String firstUserTaskActivityId(String procInstId) {
        List<HistoricActivityInstance> acts = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(procInstId)
                .activityType("userTask")
                .orderByHistoricActivityInstanceStartTime().asc()
                .listPage(0, 1);
        if (acts == null || acts.isEmpty()) {
            throw new BizException(ErrorCode.FLOW_STATE_CONFLICT, "流程尚无审批节点，无法退回");
        }
        return acts.get(0).getActivityId();
    }

    /** 发起人：优先 Flowable startedBy（发起时 Authentication 记录），兜底 initiator 变量。 */
    String requireInitiator(String procInstId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).singleResult();
        String startedBy = pi == null ? null : pi.getStartUserId();
        if (startedBy == null || startedBy.isBlank()) {
            Object v = runtimeService.getVariable(procInstId, "initiator");
            startedBy = v == null ? null : String.valueOf(v);
        }
        if (startedBy == null || startedBy.isBlank()) {
            throw new BizException(ErrorCode.FLOW_STATE_CONFLICT, "无法确定流程发起人，退回失败");
        }
        return startedBy;
    }

    /**
     * 委托代办留痕判定（FLW-F-009）：操作人（被委托人 to_user）处理的是生效委托中
     * from_user 名下的任务时，返回 from_user 作为 delegateFrom；否则 null。
     * 流程定义 Key 取 processDefinitionId 前缀（':' 之前）。
     */
    Long resolveDelegateFrom(Task task, Long operator) {
        if (operator == null || task == null) {
            return null;
        }
        String defId = task.getProcessDefinitionId();
        String procDefKey = defId == null ? null : (defId.contains(":") ? defId.substring(0, defId.indexOf(':')) : defId);
        Long tenantId = TraceContext.getTenantId();
        return delegateService.resolveDelegateFrom(operator, tenantId,
                task.getAssignee(), procDefKey);
    }

    Task requireTask(String taskId) {
        String tenantId = requireTenant();
        Task task = taskService.createTaskQuery().taskId(taskId).taskTenantId(tenantId).singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.FLOW_NOT_FOUND, "任务不存在或已处理");
        }
        return task;
    }

    Task requireTaskAndCheck(String taskId) {
        Task task = requireTask(taskId);
        Long userId = TraceContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少用户身份");
        }
        String uid = String.valueOf(userId);
        if (task.getAssignee() != null && task.getAssignee().equals(uid)) {
            return task;
        }
        long candidate = taskService.createTaskQuery().taskId(taskId).taskCandidateUser(uid).count();
        if (candidate > 0) {
            return task;
        }
        // 委托代办放行（FLW-F-009）：任务 assignee 为生效委托的发起人时，被委托人可处理
        if (resolveDelegateFrom(task, userId) != null) {
            return task;
        }
        throw new BizException(ErrorCode.FLOW_NO_PERMISSION, "无权处理该流程任务");
    }

    void record(String taskId, TaskActionType action, Long operator,
                TaskActionRequest req, Long delegateFrom) {
        TaskAction a = new TaskAction();
        a.setTaskId(taskId);
        a.setAction(action.name());
        a.setOperator(operator == null ? 0L : operator);
        a.setComment(req == null ? null : req.comment());
        a.setAttachments(req == null || req.attachments() == null ? null : JsonUtil.toJson(req.attachments()));
        a.setFormValues(req == null || req.formValues() == null ? null : JsonUtil.toJson(req.formValues()));
        a.setDelegateFrom(delegateFrom);
        a.setTs(LocalDateTime.now());
        taskActionMapper.insert(a);
    }

    /** 实例结束：回写映射状态 + 发布终态事件 + 按终态结论回调业务域（回调失败不影响审批）。 */
    void finishInstance(String procInstId, String outcome, boolean approved, String comment) {
        try {
            BizRelation rel = bizRelationService.requireByProcInstId(procInstId);
            bizRelationService.updateStatus(rel, InstanceStatus.FINISHED);
            eventOutboxService.publish(EventOutboxService.TOPIC_INSTANCE_FINISHED, rel.getTenantId(), rel.getBizKey(),
                    Map.of("procInstId", procInstId, "bizType", rel.getBizType(), "bizKey", rel.getBizKey(),
                            "outcome", outcome));
            callbackDispatcher.dispatchOnTerminal(rel.getBizType(), rel.getBizKey(), rel.getTenantId(),
                    approved, outcome, comment, procInstId);
        } catch (BizException ignored) {
            // 无映射（如手工测试流程）时跳过状态回写与回调，仅记录
        }
    }

    /** 终止映射；返回映射（无映射时返回 null），供驳回分支以 bizType 路由回调。 */
    BizRelation terminateRelation(String procInstId) {
        try {
            BizRelation rel = bizRelationService.requireByProcInstId(procInstId);
            bizRelationService.updateStatus(rel, InstanceStatus.TERMINATED);
            return rel;
        } catch (BizException ignored) {
            // 无映射时跳过
            return null;
        }
    }

    Long requireOperator() {
        Long userId = TraceContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少用户身份");
        }
        return userId;
    }

    Long requireTarget(TaskActionRequest req) {
        if (req == null || req.targetUserId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "targetUserId 不能为空");
        }
        return req.targetUserId();
    }

    Long parseLong(String v) {
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    String requireTenant() {
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null || tenantId == 0L) {
            throw new BizException(ErrorCode.TENANT_INVALID);
        }
        return String.valueOf(tenantId);
    }
}

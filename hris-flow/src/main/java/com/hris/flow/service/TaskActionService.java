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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务操作服务（FLW-F-007，docs/04 §5）：同意/驳回/转办/加签/撤回。
 * 每个动作落 flow_ext_task_action 留痕。
 */
@Service
public class TaskActionService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final TaskActionMapper taskActionMapper;
    private final BizRelationService bizRelationService;
    private final EventOutboxService eventOutboxService;
    private final InstanceService instanceService;
    private final ApprovalCallbackDispatcher callbackDispatcher;

    public TaskActionService(TaskService taskService, RuntimeService runtimeService,
                             HistoryService historyService, TaskActionMapper taskActionMapper,
                             BizRelationService bizRelationService, EventOutboxService eventOutboxService,
                             InstanceService instanceService, ApprovalCallbackDispatcher callbackDispatcher) {
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.taskActionMapper = taskActionMapper;
        this.bizRelationService = bizRelationService;
        this.eventOutboxService = eventOutboxService;
        this.instanceService = instanceService;
        this.callbackDispatcher = callbackDispatcher;
    }

    /** 同意：完成任务，若为末节点则结束实例并回调业务域。 */
    @Transactional(rollbackFor = Exception.class)
    public void approve(String taskId, TaskActionRequest req) {
        Task task = requireTaskAndCheck(taskId);
        Long tenantId = TraceContext.getTenantId();
        Long operator = TraceContext.getUserId();
        record(taskId, TaskActionType.APPROVE, operator, req, null);
        taskService.complete(taskId);
        eventOutboxService.publish(EventOutboxService.TOPIC_TASK_COMPLETED, tenantId,
                task.getProcessInstanceId(),
                Map.of("taskId", taskId, "procInstId", task.getProcessInstanceId(), "outcome", "APPROVED"));
        if (runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId()).singleResult() == null) {
            finishInstance(task.getProcessInstanceId(), "APPROVED", true,
                    req == null ? null : req.comment());
        }
    }

    /**
     * 驳回（FLW-F-007 一期深化，docs/03-07 §FLW-F-007）：
     * <ul>
     *   <li>缺省（terminate 为空/false）：退回发起人重提——流程活动移回起始用户任务并指派给发起人，
     *       实例保持 RUNNING、业务单据保持 PROCESSING；发起人在退回任务上 approve 即续跑。
     *       退回目标取实例历史中最早执行的 userTask（兼容 start 后经网关路由的模板，如 ATT_LEAVE）。</li>
     *   <li>terminate=true：终止语义（等价作废），删除实例并按 REJECTED 终态回调业务域。</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(String taskId, TaskActionRequest req) {
        if (req != null && Boolean.TRUE.equals(req.terminate())) {
            terminate(taskId, req);
        } else {
            returnToInitiator(taskId, req);
        }
    }

    /** 终止：删除实例（等价作废），以 approved=false / REJECTED 回调业务域。 */
    private void terminate(String taskId, TaskActionRequest req) {
        Task task = requireTaskAndCheck(taskId);
        Long tenantId = TraceContext.getTenantId();
        Long operator = TraceContext.getUserId();
        record(taskId, TaskActionType.REJECT, operator, req, null);
        runtimeService.deleteProcessInstance(task.getProcessInstanceId(),
                "REJECTED:TERMINATED:" + (req == null || req.comment() == null ? "" : req.comment()));
        BizRelation rel = terminateRelation(task.getProcessInstanceId());
        eventOutboxService.publish(EventOutboxService.TOPIC_INSTANCE_FINISHED, tenantId,
                task.getProcessInstanceId(),
                Map.of("taskId", taskId, "procInstId", task.getProcessInstanceId(),
                        "outcome", "REJECTED", "mode", "TERMINATE"));
        if (rel != null) {
            callbackDispatcher.dispatchOnTerminal(rel.getBizType(), rel.getBizKey(), rel.getTenantId(),
                    false, "REJECTED", req == null ? null : req.comment(), task.getProcessInstanceId());
        }
    }

    /** 退回发起人重提：活动移回起始用户任务，任务指派发起人，实例与业务单据均保持进行中。 */
    private void returnToInitiator(String taskId, TaskActionRequest req) {
        Task task = requireTaskAndCheck(taskId);
        Long operator = TraceContext.getUserId();
        String procInstId = task.getProcessInstanceId();
        record(taskId, TaskActionType.REJECT, operator, withNote(req, "驳回退回发起人重提"), null);
        String initiator = requireInitiator(procInstId);
        // 当前活动 = 被驳回任务所在节点（taskDefinitionKey 即活动 ID）
        String current = task.getTaskDefinitionKey();
        String firstUserTask = firstUserTaskActivityId(procInstId);
        // 当前活动即起始任务时无需移位（如单节点模板），仅改派发起人
        if (current == null || current.isBlank()) {
            current = currentActivityId(procInstId);
        }
        if (!firstUserTask.equals(current)) {
            runtimeService.createChangeActivityStateBuilder()
                    .processInstanceId(procInstId)
                    .moveActivityIdTo(current, firstUserTask)
                    .changeState();
        }
        for (Task t : taskService.createTaskQuery()
                .processInstanceId(procInstId).taskDefinitionKey(firstUserTask).list()) {
            taskService.setAssignee(t.getId(), initiator);
        }
    }

    /** 留痕备注：在审批意见前注明动作语义。 */
    private TaskActionRequest withNote(TaskActionRequest req, String note) {
        String origin = req == null || req.comment() == null || req.comment().isBlank()
                ? null : req.comment();
        String comment = origin == null ? note : note + "：" + origin;
        return new TaskActionRequest(comment,
                req == null ? null : req.attachments(),
                req == null ? null : req.targetUserId(),
                req == null ? null : req.terminate(),
                req == null ? null : req.formValues());
    }

    /** 当前活动节点 ID（运行中实例）。 */
    private String currentActivityId(String procInstId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).singleResult();
        if (pi == null || pi.getActivityId() == null) {
            throw new BizException(ErrorCode.FLOW_STATE_CONFLICT, "流程实例不在运行中，无法退回");
        }
        return pi.getActivityId();
    }

    /** 起始用户任务：实例历史中最早执行的 userTask（兼容 start 后经网关/分支路由的模板）。 */
    private String firstUserTaskActivityId(String procInstId) {
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
    private String requireInitiator(String procInstId) {
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

    /** 转办：任务所有权转移，原审批人留痕（docs/04 §5）。 */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(String taskId, TaskActionRequest req) {
        Task task = requireTaskAndCheck(taskId);
        Long target = requireTarget(req);
        Long operator = TraceContext.getUserId();
        String from = task.getAssignee();
        record(taskId, TaskActionType.TRANSFER, operator, req, from == null ? null : parseLong(from));
        taskService.setAssignee(taskId, String.valueOf(target));
    }

    /**
     * 认领无主任务（docs/19 §9-1）：四级解析落空产生的 assignee 为空串/NULL 的任务，操作者自领。
     * 幂等：重复认领自己返回 false；已被他人认领（assignee 非空且非本人）拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean claim(String taskId, TaskActionRequest req) {
        Task task = requireTask(taskId);
        Long operator = requireOperator();
        String uid = String.valueOf(operator);
        if (uid.equals(task.getAssignee())) {
            return false;
        }
        if (task.getAssignee() != null && !task.getAssignee().isBlank()) {
            throw new BizException(ErrorCode.FLOW_NO_PERMISSION, "任务已被他人认领，无权操作");
        }
        record(taskId, TaskActionType.CLAIM, operator, req, null);
        taskService.setAssignee(taskId, uid);
        return true;
    }

    /** 指派（管理动作，docs/19 §9-1）：管理员强制把任务指给 targetUserId，无需是当前 assignee。 */
    @Transactional(rollbackFor = Exception.class)
    public void assign(String taskId, TaskActionRequest req) {
        Task task = requireTask(taskId);
        Long target = requireTarget(req);
        Long operator = requireOperator();
        String from = task.getAssignee();
        record(taskId, TaskActionType.ASSIGN, operator, req, from == null ? null : parseLong(from));
        taskService.setAssignee(taskId, String.valueOf(target));
    }

    /** 加签（MVP：追加候选人并行加签，完整前/后/并行加签链属后续增量）。 */
    @Transactional(rollbackFor = Exception.class)
    public void addSign(String taskId, TaskActionRequest req) {
        Task task = requireTaskAndCheck(taskId);
        Long target = requireTarget(req);
        Long operator = TraceContext.getUserId();
        record(taskId, TaskActionType.ADD_SIGN, operator, req, null);
        taskService.addCandidateUser(taskId, String.valueOf(target));
    }

    /** 撤回（发起人撤回，仅当无已处理节点时允许），以 approved=false / WITHDRAWN 语义通知业务域。 */
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(String taskId) {
        Task task = requireTask(taskId);
        Long operator = TraceContext.getUserId();
        record(taskId, TaskActionType.WITHDRAW, operator, null, null);
        instanceService.withdraw(task.getProcessInstanceId(), "发起人撤回");
    }

    private Task requireTask(String taskId) {
        String tenantId = requireTenant();
        Task task = taskService.createTaskQuery().taskId(taskId).taskTenantId(tenantId).singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.FLOW_NOT_FOUND, "任务不存在或已处理");
        }
        return task;
    }

    private Task requireTaskAndCheck(String taskId) {
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
        if (candidate == 0) {
            throw new BizException(ErrorCode.FLOW_NO_PERMISSION, "无权处理该流程任务");
        }
        return task;
    }

    private void record(String taskId, TaskActionType action, Long operator,
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
    private void finishInstance(String procInstId, String outcome, boolean approved, String comment) {
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
    private BizRelation terminateRelation(String procInstId) {
        try {
            BizRelation rel = bizRelationService.requireByProcInstId(procInstId);
            bizRelationService.updateStatus(rel, InstanceStatus.TERMINATED);
            return rel;
        } catch (BizException ignored) {
            // 无映射时跳过
            return null;
        }
    }

    private Long requireOperator() {
        Long userId = TraceContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少用户身份");
        }
        return userId;
    }

    private Long requireTarget(TaskActionRequest req) {
        if (req == null || req.targetUserId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "targetUserId 不能为空");
        }
        return req.targetUserId();
    }

    private Long parseLong(String v) {
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String requireTenant() {
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null || tenantId == 0L) {
            throw new BizException(ErrorCode.TENANT_INVALID);
        }
        return String.valueOf(tenantId);
    }
}

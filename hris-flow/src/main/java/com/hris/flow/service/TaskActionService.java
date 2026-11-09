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
import com.hris.flow.strategy.StrategyOutcome;
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
import java.util.Optional;

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
    private final DelegateService delegateService;
    private final TaskStrategyService strategyService;
    private final TaskOpinionService opinionService;
    /** 校验/留痕/终态回调支持：构造器内实例化，保持外部构造器签名（11 参）不变。 */
    private final TaskActionSupport support;

    public TaskActionService(TaskService taskService, RuntimeService runtimeService,
                             HistoryService historyService, TaskActionMapper taskActionMapper,
                             BizRelationService bizRelationService, EventOutboxService eventOutboxService,
                             InstanceService instanceService, ApprovalCallbackDispatcher callbackDispatcher,
                             DelegateService delegateService, TaskStrategyService strategyService,
                             TaskOpinionService opinionService) {
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.taskActionMapper = taskActionMapper;
        this.bizRelationService = bizRelationService;
        this.eventOutboxService = eventOutboxService;
        this.instanceService = instanceService;
        this.callbackDispatcher = callbackDispatcher;
        this.delegateService = delegateService;
        this.strategyService = strategyService;
        this.opinionService = opinionService;
        this.support = new TaskActionSupport(taskService, runtimeService, historyService,
                taskActionMapper, bizRelationService, eventOutboxService, callbackDispatcher,
                delegateService);
    }

    /**
     * 同意：完成任务，若为末节点则结束实例并回调业务域。
     *
     * <p>会签策略（FLW-F-008）：节点显式配置 {@code strategy} 时先评估计票——
     * 达标则提前通过并关停剩余会签任务（由 {@link TaskStrategyService} 完成），
     * 判定为「阈值不可达」时按驳回语义退回发起人；未配置策略的节点行为不变。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(String taskId, TaskActionRequest req) {
        Task task = support.requireTaskAndCheck(taskId);
        Optional<StrategyOutcome> outcome = strategyService.evaluate(task, true);
        if (outcome.isPresent() && outcome.get().rejected()) {
            returnToInitiator(taskId, task, req, "会签未达标，驳回退回发起人重提（" + outcome.get().message() + "）");
            return;
        }
        Long tenantId = TraceContext.getTenantId();
        Long operator = TraceContext.getUserId();
        support.record(taskId, TaskActionType.APPROVE, operator, req, support.resolveDelegateFrom(task, operator));
        taskService.complete(taskId);
        if (req != null) {
            opinionService.recordForAction(taskId, req.tag(), req.comment());
        }
        eventOutboxService.publish(EventOutboxService.TOPIC_TASK_COMPLETED, tenantId,
                task.getProcessInstanceId(),
                Map.of("taskId", taskId, "procInstId", task.getProcessInstanceId(), "outcome", "APPROVED"));
        if (runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId()).singleResult() == null) {
            support.finishInstance(task.getProcessInstanceId(), "APPROVED", true,
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
     *
     * <p>会签策略（FLW-F-008）：节点配置 {@code VETO} 时任一否决即整体终止（无需再传 terminate）；
     * 配置 {@code PERCENT} 且阈值已不可达时同样终止。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(String taskId, TaskActionRequest req) {
        Task task = support.requireTaskAndCheck(taskId);
        boolean strategyReject = strategyService.evaluate(task, false)
                .map(StrategyOutcome::rejected).orElse(false);
        if (req != null) {
            opinionService.recordForAction(taskId, req.tag(), req.comment());
        }
        if (strategyReject || (req != null && Boolean.TRUE.equals(req.terminate()))) {
            terminate(taskId, task, req, strategyReject);
        } else {
            returnToInitiator(taskId, task, req, "驳回退回发起人重提");
        }
    }

    /** 终止：删除实例（等价作废），以 approved=false / REJECTED 回调业务域。 */
    private void terminate(String taskId, Task task, TaskActionRequest req, boolean strategyReject) {
        Long tenantId = TraceContext.getTenantId();
        Long operator = TraceContext.getUserId();
        TaskActionRequest effective = strategyReject ? support.withNote(req, "一票否决（会签策略），流程终止") : req;
        support.record(taskId, TaskActionType.REJECT, operator, effective, support.resolveDelegateFrom(task, operator));
        runtimeService.deleteProcessInstance(task.getProcessInstanceId(),
                "REJECTED:TERMINATED:" + (effective == null || effective.comment() == null ? "" : effective.comment()));
        BizRelation rel = support.terminateRelation(task.getProcessInstanceId());
        eventOutboxService.publish(EventOutboxService.TOPIC_INSTANCE_FINISHED, tenantId,
                task.getProcessInstanceId(),
                Map.of("taskId", taskId, "procInstId", task.getProcessInstanceId(),
                        "outcome", "REJECTED", "mode", "TERMINATE"));
        if (rel != null) {
            callbackDispatcher.dispatchOnTerminal(rel.getBizType(), rel.getBizKey(), rel.getTenantId(),
                    false, "REJECTED", effective == null ? null : effective.comment(),
                    task.getProcessInstanceId());
        }
    }

    /** 退回发起人重提：活动移回起始用户任务，任务指派发起人，实例与业务单据均保持进行中。 */
    private void returnToInitiator(String taskId, Task task, TaskActionRequest req, String note) {
        Long operator = TraceContext.getUserId();
        String procInstId = task.getProcessInstanceId();
        support.record(taskId, TaskActionType.REJECT, operator, support.withNote(req, note),
                support.resolveDelegateFrom(task, operator));
        String initiator = support.requireInitiator(procInstId);
        // 当前活动 = 被驳回任务所在节点（taskDefinitionKey 即活动 ID）
        String current = task.getTaskDefinitionKey();
        String firstUserTask = support.firstUserTaskActivityId(procInstId);
        // 当前活动即起始任务时无需移位（如单节点模板），仅改派发起人
        if (current == null || current.isBlank()) {
            current = support.currentActivityId(procInstId);
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

    /** 转办：任务所有权转移，原审批人留痕（docs/04 §5）。 */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(String taskId, TaskActionRequest req) {
        Task task = support.requireTaskAndCheck(taskId);
        Long target = support.requireTarget(req);
        Long operator = TraceContext.getUserId();
        String from = task.getAssignee();
        support.record(taskId, TaskActionType.TRANSFER, operator, req, from == null ? null : support.parseLong(from));
        taskService.setAssignee(taskId, String.valueOf(target));
    }

    /**
     * 认领无主任务（docs/19 §9-1）：四级解析落空产生的 assignee 为空串/NULL 的任务，操作者自领。
     * 幂等：重复认领自己返回 false；已被他人认领（assignee 非空且非本人）拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean claim(String taskId, TaskActionRequest req) {
        Task task = support.requireTask(taskId);
        Long operator = support.requireOperator();
        String uid = String.valueOf(operator);
        if (uid.equals(task.getAssignee())) {
            return false;
        }
        if (task.getAssignee() != null && !task.getAssignee().isBlank()) {
            throw new BizException(ErrorCode.FLOW_NO_PERMISSION, "任务已被他人认领，无权操作");
        }
        support.record(taskId, TaskActionType.CLAIM, operator, req, null);
        taskService.setAssignee(taskId, uid);
        return true;
    }

    /** 指派（管理动作，docs/19 §9-1）：管理员强制把任务指给 targetUserId，无需是当前 assignee。 */
    @Transactional(rollbackFor = Exception.class)
    public void assign(String taskId, TaskActionRequest req) {
        Task task = support.requireTask(taskId);
        Long target = support.requireTarget(req);
        Long operator = support.requireOperator();
        String from = task.getAssignee();
        support.record(taskId, TaskActionType.ASSIGN, operator, req, from == null ? null : support.parseLong(from));
        taskService.setAssignee(taskId, String.valueOf(target));
    }

    /** 加签（MVP：追加候选人并行加签，完整前/后/并行加签链属后续增量）。 */
    @Transactional(rollbackFor = Exception.class)
    public void addSign(String taskId, TaskActionRequest req) {
        Task task = support.requireTaskAndCheck(taskId);
        Long target = support.requireTarget(req);
        Long operator = TraceContext.getUserId();
        support.record(taskId, TaskActionType.ADD_SIGN, operator, req, null);
        taskService.addCandidateUser(taskId, String.valueOf(target));
    }

    /** 撤回（发起人撤回，仅当无已处理节点时允许），以 approved=false / WITHDRAWN 语义通知业务域。 */
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(String taskId) {
        Task task = support.requireTask(taskId);
        Long operator = TraceContext.getUserId();
        support.record(taskId, TaskActionType.WITHDRAW, operator, null, null);
        instanceService.withdraw(task.getProcessInstanceId(), "发起人撤回");
    }

}

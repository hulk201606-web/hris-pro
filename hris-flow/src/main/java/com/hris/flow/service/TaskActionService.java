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
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务操作服务（FLW-F-007，docs/04 §5）：同意/驳回/转办/加签/撤回。
 * 每个动作落 flow_ext_task_action 留痕。
 */
@Service
public class TaskActionService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final TaskActionMapper taskActionMapper;
    private final BizRelationService bizRelationService;
    private final EventOutboxService eventOutboxService;
    private final InstanceService instanceService;

    public TaskActionService(TaskService taskService, RuntimeService runtimeService,
                             TaskActionMapper taskActionMapper, BizRelationService bizRelationService,
                             EventOutboxService eventOutboxService, InstanceService instanceService) {
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.taskActionMapper = taskActionMapper;
        this.bizRelationService = bizRelationService;
        this.eventOutboxService = eventOutboxService;
        this.instanceService = instanceService;
    }

    /** 同意：完成任务，若为末节点则结束实例。 */
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
            finishInstance(task.getProcessInstanceId(), "APPROVED");
        }
    }

    /** 驳回（MVP：终止回发起人策略，docs/04 §5 三策略之一）。 */
    @Transactional(rollbackFor = Exception.class)
    public void reject(String taskId, TaskActionRequest req) {
        Task task = requireTaskAndCheck(taskId);
        Long tenantId = TraceContext.getTenantId();
        Long operator = TraceContext.getUserId();
        record(taskId, TaskActionType.REJECT, operator, req, null);
        runtimeService.deleteProcessInstance(task.getProcessInstanceId(),
                "REJECTED:" + (req == null || req.comment() == null ? "" : req.comment()));
        terminateRelation(task.getProcessInstanceId());
        eventOutboxService.publish(EventOutboxService.TOPIC_INSTANCE_FINISHED, tenantId,
                task.getProcessInstanceId(),
                Map.of("taskId", taskId, "procInstId", task.getProcessInstanceId(), "outcome", "REJECTED"));
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

    /** 加签（MVP：追加候选人并行加签，完整前/后/并行加签链属后续增量）。 */
    @Transactional(rollbackFor = Exception.class)
    public void addSign(String taskId, TaskActionRequest req) {
        Task task = requireTaskAndCheck(taskId);
        Long target = requireTarget(req);
        Long operator = TraceContext.getUserId();
        record(taskId, TaskActionType.ADD_SIGN, operator, req, null);
        taskService.addCandidateUser(taskId, String.valueOf(target));
    }

    /** 撤回（发起人撤回，仅当无已处理节点时允许）。 */
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
        a.setDelegateFrom(delegateFrom);
        a.setTs(LocalDateTime.now());
        taskActionMapper.insert(a);
    }

    private void finishInstance(String procInstId, String outcome) {
        try {
            BizRelation rel = bizRelationService.requireByProcInstId(procInstId);
            bizRelationService.updateStatus(rel, InstanceStatus.FINISHED);
            eventOutboxService.publish(EventOutboxService.TOPIC_INSTANCE_FINISHED, rel.getTenantId(), rel.getBizKey(),
                    Map.of("procInstId", procInstId, "bizType", rel.getBizType(), "bizKey", rel.getBizKey(),
                            "outcome", outcome));
        } catch (BizException ignored) {
            // 无映射（如手工测试流程）时跳过状态回写，仅记录
        }
    }

    private void terminateRelation(String procInstId) {
        try {
            BizRelation rel = bizRelationService.requireByProcInstId(procInstId);
            bizRelationService.updateStatus(rel, InstanceStatus.TERMINATED);
        } catch (BizException ignored) {
            // 无映射时跳过
        }
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

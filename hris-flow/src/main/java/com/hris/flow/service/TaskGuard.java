package com.hris.flow.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

/**
 * 任务访问守卫（FLW-F-020 复用）：统一「任务存在 + 本租户 + 办理人」判定。
 *
 * <p>办理人 = 当前 assignee / 候选人 / 生效委托的被委托人，与 TaskActionService 的审批动作
 * 判定同源；供节点附件、意见标签等旁路写操作复用，避免各自实现一套规则。</p>
 */
@Service
public class TaskGuard {

    private final TaskService taskService;
    private final DelegateService delegateService;

    public TaskGuard(TaskService taskService, DelegateService delegateService) {
        this.taskService = taskService;
        this.delegateService = delegateService;
    }

    /** 本租户运行中的任务；已完成/不存在一律 30404。 */
    public Task requireTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "taskId 不能为空");
        }
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null || tenantId == 0L) {
            throw new BizException(ErrorCode.TENANT_INVALID);
        }
        Task task = taskService.createTaskQuery().taskId(taskId)
                .taskTenantId(String.valueOf(tenantId)).singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.FLOW_NOT_FOUND, "任务不存在或已处理");
        }
        return task;
    }

    /** 仅办理人可写：assignee / 候选人 / 委托代办放行，其余 30423。 */
    public Task requireWritableTask(String taskId) {
        Task task = requireTask(taskId);
        Long userId = TraceContext.getUserId();
        if (userId == null || userId == 0L) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少用户身份");
        }
        String uid = String.valueOf(userId);
        if (uid.equals(task.getAssignee())) {
            return task;
        }
        long candidate = taskService.createTaskQuery().taskId(task.getId()).taskCandidateUser(uid).count();
        if (candidate > 0) {
            return task;
        }
        // 委托代办放行（FLW-F-009）；0/空视为无委托
        Long delegatedFrom = delegateFrom(task, userId);
        if (delegatedFrom != null && delegatedFrom != 0L) {
            return task;
        }
        throw new BizException(ErrorCode.FLOW_NO_PERMISSION, "无权处理该流程任务");    }

    /** 委托来源判定（FLW-F-009），非委托场景返回 null。 */
    public Long delegateFrom(Task task, Long operator) {
        if (operator == null || task == null) {
            return null;
        }
        String defId = task.getProcessDefinitionId();
        String procDefKey = defId == null ? null
                : (defId.contains(":") ? defId.substring(0, defId.indexOf(':')) : defId);
        return delegateService.resolveDelegateFrom(operator, TraceContext.getTenantId(),
                task.getAssignee(), procDefKey);
    }
}

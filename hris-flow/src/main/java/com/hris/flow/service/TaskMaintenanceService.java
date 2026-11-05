package com.hris.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.entity.TaskAction;
import com.hris.flow.entity.TimeoutCfg;
import com.hris.flow.enums.TaskActionType;
import com.hris.flow.mapper.TaskActionMapper;
import com.hris.flow.mapper.TimeoutCfgMapper;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程任务维护操作（docs/19 §10.5-C：FLW-F-010 超时升级 / §10.5-A2 待办随负责人重算）。
 * 仅供内部端点（/internal/flow/tasks/*）调用：无登录用户，留痕 operator=0（系统）。
 */
@Service
public class TaskMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(TaskMaintenanceService.class);

    private final TaskService taskService;
    private final TaskActionMapper taskActionMapper;
    private final TimeoutCfgMapper timeoutCfgMapper;

    public TaskMaintenanceService(TaskService taskService, TaskActionMapper taskActionMapper,
                                  TimeoutCfgMapper timeoutCfgMapper) {
        this.taskService = taskService;
        this.taskActionMapper = taskActionMapper;
        this.timeoutCfgMapper = timeoutCfgMapper;
    }

    /**
     * 超时升级（FLW-F-010）：扫描本租户运行中任务，关联模型配置了 timeout_cfg 且已超期
     * （按 escalateAfterH 小时）的任务：
     * <ul>
     *   <li>cfg.escalate_to 非空 → taskService.setAssignee 指派升级目标；</li>
     *   <li>escalate_to 为空 → 仅落 action=ESCALATE 留痕 + log，不改派。</li>
     * </ul>
     * 幂等：已升级过的任务按 flow_ext_task_action(taskId, ESCALATE) 判断，不重复执行。
     *
     * @return scanned=扫描数 escalated=本次执行升级数 skippedIdempotent=已升级跳过数
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> escalateOverdue() {
        String tenantId = requireTenant();
        List<Task> tasks = taskService.createTaskQuery().taskTenantId(tenantId).active().list();
        Map<String, List<TimeoutCfg>> cfgByDef = cfgByProcDefIds(tasks);
        int escalated = 0;
        int skipped = 0;
        for (Task t : tasks) {
            TimeoutCfg cfg = matchCfg(cfgByDef, t);
            if (cfg == null || !isOverdue(t, cfg)) {
                continue;
            }
            if (alreadyEscalated(t.getId())) {
                skipped++;
                continue;
            }
            String from = t.getAssignee();
            if (cfg.getEscalateTo() != null) {
                taskService.setAssignee(t.getId(), String.valueOf(cfg.getEscalateTo()));
            }
            record(t.getId(), cfg, from);
            escalated++;
        }
        log.info("超时升级扫描完成: tenantId={}, scanned={}, escalated={}, skipped={}",
                tenantId, tasks.size(), escalated, skipped);
        Map<String, Integer> result = new HashMap<>();
        result.put("scanned", tasks.size());
        result.put("escalated", escalated);
        result.put("skippedIdempotent", skipped);
        return result;
    }

    /**
     * 待办随负责人重算（docs/19 §10.5-A2）：本租户所有运行中实例里 assignee=oldEmpId 的
     * 运行时任务改为 newEmpId，留痕 action=ASSIGN（comment 注明负责人变更重算）。
     * 幂等：重复调用结果一致（无 oldEmpId 任务时 reassigned=0）。
     *
     * @return reassigned=本次改派任务数
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> reassignManager(Long oldEmpId, Long newEmpId) {
        if (oldEmpId == null || newEmpId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "oldEmpId/newEmpId 不能为空");
        }
        String tenantId = requireTenant();
        List<Task> tasks = taskService.createTaskQuery()
                .taskTenantId(tenantId)
                .taskAssignee(String.valueOf(oldEmpId))
                .active()
                .list();
        for (Task t : tasks) {
            taskService.setAssignee(t.getId(), String.valueOf(newEmpId));
            TaskAction a = new TaskAction();
            a.setTaskId(t.getId());
            a.setAction(TaskActionType.ASSIGN.name());
            a.setOperator(0L);
            a.setComment("负责人变更重算：" + oldEmpId + " → " + newEmpId);
            a.setDelegateFrom(oldEmpId);
            a.setTs(LocalDateTime.now());
            taskActionMapper.insert(a);
        }
        log.info("负责人变更待办重算: tenantId={}, old={}, new={}, reassigned={}",
                tenantId, oldEmpId, newEmpId, tasks.size());
        return Map.of("reassigned", tasks.size());
    }

    private Map<String, List<TimeoutCfg>> cfgByProcDefIds(List<Task> tasks) {
        List<String> defIds = tasks.stream().map(Task::getProcessDefinitionId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (defIds.isEmpty()) {
            return Map.of();
        }
        List<TimeoutCfg> cfgs = timeoutCfgMapper.selectList(new LambdaQueryWrapper<TimeoutCfg>()
                .in(TimeoutCfg::getProcDefId, defIds));
        return cfgs.stream()
                .filter(c -> c.getProcDefId() != null)
                .collect(Collectors.groupingBy(TimeoutCfg::getProcDefId));
    }

    private TimeoutCfg matchCfg(Map<String, List<TimeoutCfg>> cfgByDef, Task t) {
        if (t.getProcessDefinitionId() == null) {
            return null;
        }
        List<TimeoutCfg> cfgs = cfgByDef.get(t.getProcessDefinitionId());
        if (cfgs == null) {
            return null;
        }
        return cfgs.stream()
                .filter(c -> t.getTaskDefinitionKey() != null
                        && t.getTaskDefinitionKey().equals(c.getNodeId()))
                .findFirst().orElse(null);
    }

    /** 超期判定：escalateAfterH>0 且任务创建时间已超期（口径与 TaskQueryService.overdue 一致）。 */
    private boolean isOverdue(Task task, TimeoutCfg cfg) {
        return cfg.getEscalateAfterH() != null && cfg.getEscalateAfterH() > 0
                && task.getCreateTime() != null
                && System.currentTimeMillis() - task.getCreateTime().getTime()
                        > cfg.getEscalateAfterH() * 3600_000L;
    }

    /** 幂等判断：该任务已落过 ESCALATE 留痕则跳过。 */
    private boolean alreadyEscalated(String taskId) {
        Long cnt = taskActionMapper.selectCount(new LambdaQueryWrapper<TaskAction>()
                .eq(TaskAction::getTaskId, taskId)
                .eq(TaskAction::getAction, TaskActionType.ESCALATE.name()));
        return cnt != null && cnt > 0;
    }

    private void record(String taskId, TimeoutCfg cfg, String fromAssignee) {
        TaskAction a = new TaskAction();
        a.setTaskId(taskId);
        a.setAction(TaskActionType.ESCALATE.name());
        a.setOperator(0L);
        a.setComment("超时升级：超过 " + cfg.getEscalateAfterH() + " 小时"
                + (cfg.getEscalateTo() == null ? "（未配置升级目标，仅留痕）"
                        : "，升级指派至 " + cfg.getEscalateTo()));
        a.setDelegateFrom(parseLong(fromAssignee));
        a.setTs(LocalDateTime.now());
        taskActionMapper.insert(a);
    }

    private Long parseLong(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
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

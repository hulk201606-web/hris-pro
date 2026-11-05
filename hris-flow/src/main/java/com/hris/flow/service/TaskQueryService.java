package com.hris.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.PageResult;
import com.hris.flow.dto.TaskView;
import com.hris.flow.entity.BizRelation;
import com.hris.flow.entity.FlowModel;
import com.hris.flow.entity.TimeoutCfg;
import com.hris.flow.mapper.BizRelationMapper;
import com.hris.flow.mapper.FlowModelMapper;
import com.hris.flow.mapper.TimeoutCfgMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 待办中心查询（FLW-F-006/011）：待办/已办/抄送/我发起，分页聚合。
 *
 * <p>多租户：所有查询强制带 Flowable 原生 tenantId；用户维度取自网关透传的 X-User-Id
 * （TraceContext.userId）。</p>
 */
@Service
public class TaskQueryService {

    private static final Logger log = LoggerFactory.getLogger(TaskQueryService.class);

    private final TaskService taskService;
    private final HistoryService historyService;
    private final BizRelationMapper bizRelationMapper;
    private final FlowModelMapper flowModelMapper;
    private final TimeoutCfgMapper timeoutCfgMapper;

    public TaskQueryService(TaskService taskService, HistoryService historyService,
                            BizRelationMapper bizRelationMapper, FlowModelMapper flowModelMapper,
                            TimeoutCfgMapper timeoutCfgMapper) {
        this.taskService = taskService;
        this.historyService = historyService;
        this.bizRelationMapper = bizRelationMapper;
        this.flowModelMapper = flowModelMapper;
        this.timeoutCfgMapper = timeoutCfgMapper;
    }

    /** 我的待办（候选人或已指派）。 */
    public PageResult<TaskView> todo(long page, long size) {
        String tenantId = requireTenant();
        String uid = requireUser();
        TaskQuery q = taskService.createTaskQuery()
                .taskTenantId(tenantId)
                .taskCandidateOrAssigned(uid);
        long total = q.count();
        List<Task> tasks = q.orderByTaskCreateTime().desc().listPage(offset(page, size), safeSize(size));
        return new PageResult<>(buildViews(tasks), page, size, total);
    }

    /**
     * 待指派任务（docs/19 §9-1）：本租户运行时无主任务，供管理员认领/指派。
     * Flowable taskUnassigned() 只匹配 ASSIGNEE_ IS NULL，而待指派降级创建的任务
     * assignee 为空串，故用 or 块同时覆盖两种情况。
     */
    public PageResult<TaskView> unassigned(long page, long size, String procDefKey) {
        String tenantId = requireTenant();
        TaskQuery q = taskService.createTaskQuery().taskTenantId(tenantId);
        if (procDefKey != null && !procDefKey.isBlank()) {
            q.processDefinitionKey(procDefKey);
        }
        q.or().taskUnassigned().taskAssignee("").endOr();
        long total = q.count();
        List<Task> tasks = q.orderByTaskCreateTime().desc().listPage(offset(page, size), safeSize(size));
        return new PageResult<>(buildViews(tasks), page, size, total);
    }

    /** 我的已办。 */
    public PageResult<TaskView> done(long page, long size) {
        String tenantId = requireTenant();
        String uid = requireUser();
        HistoricTaskInstanceQuery q = historyService.createHistoricTaskInstanceQuery()
                .taskTenantId(tenantId)
                .taskAssignee(uid)
                .finished();
        long total = q.count();
        List<HistoricTaskInstance> tasks = q.orderByHistoricTaskInstanceEndTime().desc()
                .listPage(offset(page, size), safeSize(size));
        Map<String, BizRelation> relMap = bizByInstIds(tasks.stream()
                .map(HistoricTaskInstance::getProcessInstanceId).filter(java.util.Objects::nonNull).distinct().toList());
        List<TaskView> views = new ArrayList<>();
        for (HistoricTaskInstance t : tasks) {
            BizRelation rel = relMap.get(t.getProcessInstanceId());
            views.add(new TaskView(t.getId(), t.getProcessInstanceId(), bizTypeOf(rel),
                    bizTypeOf(rel), bizKeyOf(rel), t.getName(), t.getAssignee(),
                    "DONE", fmt(t.getStartTime()), fmt(t.getCreateTime()), fmt(t.getEndTime()),
                    t.getDurationInMillis() == null ? 0L : t.getDurationInMillis(), null, false));
        }
        return new PageResult<>(views, page, size, total);
    }

    /**
     * 抄送我的。Flowable 原生无抄送概念，MVP 返回空页；抄送需依赖 platform 通知（FLW-F-006）
     * 或扩展 flow_ext 抄送表，属后续增量（见交付回传风险点）。
     */
    public PageResult<TaskView> cc(long page, long size) {
        requireTenant();
        requireUser();
        log.debug("cc 抄送查询 MVP 未实现，返回空页");
        return new PageResult<>(List.of(), page, size, 0L);
    }

    /** 我发起的。 */
    public PageResult<TaskView> started(long page, long size) {
        String tenantId = requireTenant();
        String uid = requireUser();
        HistoricProcessInstanceQuery q = historyService.createHistoricProcessInstanceQuery()
                .processInstanceTenantId(tenantId)
                .startedBy(uid);
        long total = q.count();
        List<HistoricProcessInstance> instances = q.orderByProcessInstanceStartTime().desc()
                .listPage(offset(page, size), safeSize(size));
        List<TaskView> views = new ArrayList<>();
        for (HistoricProcessInstance p : instances) {
            String name = p.getName() != null ? p.getName() : p.getProcessDefinitionName();
            String status = p.getEndTime() == null ? "RUNNING" : "FINISHED";
            views.add(new TaskView(null, p.getId(), p.getProcessDefinitionKey(),
                    p.getProcessDefinitionKey(), p.getBusinessKey(), name, null,
                    status, fmt(p.getStartTime()), fmt(p.getStartTime()), fmt(p.getEndTime()),
                    p.getDurationInMillis() == null ? 0L : p.getDurationInMillis(), null, false));
        }
        return new PageResult<>(views, page, size, total);
    }

    /** 运行时任务视图组装（待办/待指派共用）：业务映射 + form_schema_json + 超期标记。 */
    private List<TaskView> buildViews(List<Task> tasks) {
        Map<String, BizRelation> relMap = bizByInstIds(tasks.stream()
                .map(Task::getProcessInstanceId).filter(java.util.Objects::nonNull).distinct().toList());
        // 死代码最小激活（docs/19 §8.7-6）：form_schema_json 随待办返回 + 超期 overdue 标记
        Map<String, String> schemaMap = formSchemaByKeys(tasks.stream()
                .map(t -> procKeyOf(t, relMap.get(t.getProcessInstanceId()))).collect(Collectors.toSet()));
        Map<String, TimeoutCfg> cfgMap = timeoutCfgByProcDefIds(tasks.stream()
                .map(Task::getProcessDefinitionId).filter(java.util.Objects::nonNull).distinct().toList());
        List<TaskView> views = new ArrayList<>();
        for (Task t : tasks) {
            BizRelation rel = relMap.get(t.getProcessInstanceId());
            String procKey = procKeyOf(t, rel);
            TimeoutCfg cfg = t.getProcessDefinitionId() == null ? null
                    : cfgMap.get(t.getProcessDefinitionId() + "#" + t.getTaskDefinitionKey());
            views.add(new TaskView(t.getId(), t.getProcessInstanceId(), bizTypeOf(rel),
                    bizTypeOf(rel), bizKeyOf(rel), t.getName(), t.getAssignee(),
                    "TODO", fmt(t.getCreateTime()), fmt(t.getCreateTime()), null, 0L,
                    schemaMap.get(procKey), isOverdue(t, cfg)));
        }
        return views;
    }

    private Map<String, BizRelation> bizByInstIds(List<String> procInstIds) {
        if (procInstIds.isEmpty()) {
            return Map.of();
        }
        List<BizRelation> rels = bizRelationMapper.selectList(new LambdaQueryWrapper<BizRelation>()
                .in(BizRelation::getProcInstId, procInstIds));
        return rels.stream().collect(Collectors.toMap(BizRelation::getProcInstId, r -> r, (a, b) -> a));
    }

    private String bizTypeOf(BizRelation rel) {
        return rel == null ? null : rel.getBizType();
    }

    private String bizKeyOf(BizRelation rel) {
        return rel == null ? null : rel.getBizKey();
    }

    /** 流程定义 key：优先取业务映射 bizType（兼作定义 key），缺省从 processDefinitionId 解析。 */
    private String procKeyOf(Task task, BizRelation rel) {
        if (rel != null && rel.getBizType() != null && !rel.getBizType().isBlank()) {
            return rel.getBizType();
        }
        String defId = task.getProcessDefinitionId();
        if (defId == null) {
            return null;
        }
        int idx = defId.indexOf(':');
        return idx > 0 ? defId.substring(0, idx) : defId;
    }

    /** 按流程定义 key 批量取最新版模型的 form_schema_json（死代码激活：前端可渲染表单）。 */
    private Map<String, String> formSchemaByKeys(java.util.Set<String> keys) {
        Map<String, String> result = new java.util.HashMap<>();
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            List<FlowModel> models = flowModelMapper.selectList(new LambdaQueryWrapper<FlowModel>()
                    .eq(FlowModel::getProcDefKey, key)
                    .orderByDesc(FlowModel::getVersion)
                    .last("limit 1"));
            if (models != null && !models.isEmpty() && models.get(0).getFormSchemaJson() != null) {
                result.put(key, models.get(0).getFormSchemaJson());
            }
        }
        return result;
    }

    /** 按流程定义 ID 批量取超时配置，key = procDefId#nodeId。 */
    private Map<String, TimeoutCfg> timeoutCfgByProcDefIds(List<String> procDefIds) {
        if (procDefIds.isEmpty()) {
            return Map.of();
        }
        List<TimeoutCfg> cfgs = timeoutCfgMapper.selectList(new LambdaQueryWrapper<TimeoutCfg>()
                .in(TimeoutCfg::getProcDefId, procDefIds));
        if (cfgs == null || cfgs.isEmpty()) {
            return Map.of();
        }
        return cfgs.stream()
                .filter(c -> c.getProcDefId() != null && c.getNodeId() != null)
                .collect(Collectors.toMap(c -> c.getProcDefId() + "#" + c.getNodeId(), c -> c, (a, b) -> a));
    }

    /** 超期判定：配置了超时时长（escalateAfterH 小时）且任务创建时间已超期（不做自动催办/升级）。 */
    private boolean isOverdue(Task task, TimeoutCfg cfg) {
        return cfg != null && cfg.getEscalateAfterH() != null && cfg.getEscalateAfterH() > 0
                && task.getCreateTime() != null
                && System.currentTimeMillis() - task.getCreateTime().getTime()
                        > cfg.getEscalateAfterH() * 3600_000L;
    }

    private int offset(long page, long size) {
        long p = Math.max(1, page);
        return (int) ((p - 1) * Math.max(1, size));
    }

    private int safeSize(long size) {
        return (int) Math.min(Math.max(1, size), 200);
    }

    private String requireTenant() {
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null || tenantId == 0L) {
            throw new BizException(ErrorCode.TENANT_INVALID);
        }
        return String.valueOf(tenantId);
    }

    private String requireUser() {
        Long userId = TraceContext.getUserId();
        if (userId == null || userId == 0L) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少用户身份，无法查询个人任务");
        }
        return String.valueOf(userId);
    }

    private String fmt(Date d) {
        return d == null ? null : java.time.Instant.ofEpochMilli(d.getTime()).toString();
    }
}

package com.hris.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.PageResult;
import com.hris.flow.dto.TaskView;
import com.hris.flow.entity.BizRelation;
import com.hris.flow.mapper.BizRelationMapper;
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

    public TaskQueryService(TaskService taskService, HistoryService historyService,
                            BizRelationMapper bizRelationMapper) {
        this.taskService = taskService;
        this.historyService = historyService;
        this.bizRelationMapper = bizRelationMapper;
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
        Map<String, BizRelation> relMap = bizByInstIds(tasks.stream()
                .map(Task::getProcessInstanceId).filter(java.util.Objects::nonNull).distinct().toList());
        List<TaskView> views = new ArrayList<>();
        for (Task t : tasks) {
            BizRelation rel = relMap.get(t.getProcessInstanceId());
            views.add(new TaskView(t.getId(), t.getProcessInstanceId(), bizTypeOf(rel),
                    bizTypeOf(rel), bizKeyOf(rel), t.getName(), t.getAssignee(),
                    "TODO", fmt(t.getCreateTime()), fmt(t.getCreateTime()), null, 0L));
        }
        return new PageResult<>(views, page, size, total);
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
                    t.getDurationInMillis() == null ? 0L : t.getDurationInMillis()));
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
                    p.getDurationInMillis() == null ? 0L : p.getDurationInMillis()));
        }
        return new PageResult<>(views, page, size, total);
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

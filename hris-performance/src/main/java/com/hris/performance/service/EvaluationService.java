package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.common.security.AuthContextHolder;
import com.hris.performance.dto.EvaluationDtos.EvaluationCreateRequest;
import com.hris.performance.dto.EvaluationDtos.EvaluationResponse;
import com.hris.performance.dto.EvaluationDtos.EvaluationUpdateRequest;
import com.hris.performance.entity.Cycle;
import com.hris.performance.entity.EvalTask;
import com.hris.performance.entity.Evaluation;
import com.hris.performance.mapper.CycleMapper;
import com.hris.performance.mapper.EvalTaskMapper;
import com.hris.performance.mapper.EvaluationMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluation 服务（CRUD + 周期状态守卫 + 互见掩码）。
 *
 * <p>PRF-F-002：评价单无 cycleId，经 taskId → EvalTask → cycleId 两跳定位周期；
 * 自评只在 ACTIVE 自评窗口可写，上级/互评在 ACTIVE/SCORING 可写，DRAFT/CLOSED 拒绝。</p>
 *
 * <p>PRF-F-004：读取（详情/列表）按 {@link ScoreVisibilityRule} 组装响应——越权视角的
 * scores/comment 置 null，响应字段保留（前端据 null 隐藏内容，不据此判断"未评分"）。</p>
 */
@Service
public class EvaluationService {

    private final EvaluationMapper mapper;
    private final EvalTaskMapper evalTaskMapper;
    private final CycleMapper cycleMapper;

    public EvaluationService(EvaluationMapper mapper, EvalTaskMapper evalTaskMapper,
                             CycleMapper cycleMapper) {
        this.mapper = mapper;
        this.evalTaskMapper = evalTaskMapper;
        this.cycleMapper = cycleMapper;
    }

    /** 按主键查询详情：按请求者视角掩码。 */
    public EvaluationResponse get(Long id) {
        Evaluation e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Evaluation 不存在");
        }
        EvalTask task = e.getTaskId() == null ? null : evalTaskMapper.selectById(e.getTaskId());
        return toMasked(e, task, cycleStatusOf(task));
    }

    /** 分页查询列表：批量装载任务与周期后逐条掩码（避免 N+1）。 */
    public PageResult<EvaluationResponse> list(long page, long size) {
        IPage<Evaluation> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<Evaluation> rows = pg.getRecords();
        Map<Long, EvalTask> tasks = loadTasks(rows);
        Map<Long, String> cycles = loadCycleStatus(tasks.values());
        List<EvaluationResponse> records = new ArrayList<>(rows.size());
        for (Evaluation e : rows) {
            EvalTask task = e.getTaskId() == null ? null : tasks.get(e.getTaskId());
            String cycleStatus = task == null ? null : cycles.get(task.getCycleId());
            records.add(toMasked(e, task, cycleStatus));
        }
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建评价：按任务角色做评分窗口守卫。 */
    @Transactional(rollbackFor = Exception.class)
    public EvaluationResponse create(EvaluationCreateRequest req) {
        EvalTask task = ensureScorable(req.taskId());
        Evaluation e = new Evaluation();
        e.setTaskId(req.taskId());
        e.setEvaluatorId(req.evaluatorId());
        e.setScores(req.scores());
        e.setComment(req.comment());
        mapper.insert(e);
        return toMasked(e, task, cycleStatusOf(task));
    }

    /** 更新评价：原任务与新任务都要过评分窗口守卫。 */
    @Transactional(rollbackFor = Exception.class)
    public EvaluationResponse update(Long id, EvaluationUpdateRequest req) {
        Evaluation e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Evaluation 不存在");
        }
        EvalTask task = ensureScorable(e.getTaskId());
        if (req.taskId() != null && !req.taskId().equals(e.getTaskId())) {
            task = ensureScorable(req.taskId());
            e.setTaskId(req.taskId());
        }
        e.setEvaluatorId(req.evaluatorId() == null ? e.getEvaluatorId() : req.evaluatorId());
        e.setScores(req.scores());
        e.setComment(req.comment());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toMasked(e, task, cycleStatusOf(task));
    }

    /** 删除评价：评分窗口关闭后不再允许删除（防事后抹分）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Evaluation e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Evaluation 不存在");
        }
        ensureScorable(e.getTaskId());
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Evaluation 不存在");
        }
    }

    /**
     * 评分窗口守卫：按任务所属周期状态 + 评价人角色判定（租户条件由插件注入），返回任务供响应组装复用。
     * 任务/周期缺失时不拦截，存在性由外层语义保证。
     */
    private EvalTask ensureScorable(Long taskId) {
        if (taskId == null) {
            return null;
        }
        EvalTask task = evalTaskMapper.selectById(taskId);
        if (task == null || task.getCycleId() == null) {
            return task;
        }
        Cycle cycle = cycleMapper.selectById(task.getCycleId());
        if (cycle != null) {
            CycleStateMachine.requireScoringWritable(cycle.getStatus(), task.getEvaluatorRole());
        }
        return task;
    }

    /** 组装响应并做互见掩码：不可见时 scores/comment 置 null，字段本身保留。 */
    private EvaluationResponse toMasked(Evaluation e, EvalTask task, String cycleStatus) {
        if (task == null) {
            // 无任务上下文（脏数据）：fail-closed，不返回任何过程分
            return new EvaluationResponse(e.getId(), e.getTaskId(), e.getEvaluatorId(),
                    null, null, e.getCreatedAt(), e.getUpdatedAt());
        }
        ScoreVisibilityRule.Viewer viewer = ScoreVisibilityRule.viewerOf(
                AuthContextHolder.getUserId(), e.getEvaluatorId(), task.getEmpId(),
                AuthContextHolder.getPerms());
        ScoreVisibilityRule.Stage stage = ScoreVisibilityRule.stageOf(cycleStatus);
        boolean visible = ScoreVisibilityRule.scoresVisible(viewer, task.getEvaluatorRole(), stage);
        return new EvaluationResponse(e.getId(), e.getTaskId(), e.getEvaluatorId(),
                visible ? e.getScores() : null,
                visible ? e.getComment() : null,
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private Map<Long, EvalTask> loadTasks(List<Evaluation> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Evaluation e : rows) {
            if (e.getTaskId() != null) {
                ids.add(e.getTaskId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, EvalTask> map = new HashMap<>(ids.size());
        for (EvalTask t : evalTaskMapper.selectBatchIds(ids)) {
            map.put(t.getId(), t);
        }
        return map;
    }

    private Map<Long, String> loadCycleStatus(Collection<EvalTask> tasks) {
        Set<Long> ids = new LinkedHashSet<>();
        for (EvalTask t : tasks) {
            if (t.getCycleId() != null) {
                ids.add(t.getCycleId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>(ids.size());
        for (Cycle c : cycleMapper.selectBatchIds(ids)) {
            map.put(c.getId(), c.getStatus());
        }
        return map;
    }

    private String cycleStatusOf(EvalTask task) {
        if (task == null || task.getCycleId() == null) {
            return null;
        }
        Cycle c = cycleMapper.selectById(task.getCycleId());
        return c == null ? null : c.getStatus();
    }
}

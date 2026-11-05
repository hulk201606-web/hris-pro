package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.util.List;

/**
 * Evaluation 服务（CRUD）。
 *
 * <p>周期锁守卫（§8.5-4）：评价单无 cycleId，经 taskId → EvalTask → cycleId 两跳定位
 * 所属周期；周期 status 为 PUBLISHED/CLOSED 时 create/update/delete 一律拒绝。</p>
 */
@Service
public class EvaluationService {

    /** 周期锁定态：已发布 */
    private static final String CYCLE_PUBLISHED = "PUBLISHED";
    /** 周期锁定态：已关闭 */
    private static final String CYCLE_CLOSED = "CLOSED";

    private final EvaluationMapper mapper;
    private final EvalTaskMapper evalTaskMapper;
    private final CycleMapper cycleMapper;

    public EvaluationService(EvaluationMapper mapper, EvalTaskMapper evalTaskMapper,
                             CycleMapper cycleMapper) {
        this.mapper = mapper;
        this.evalTaskMapper = evalTaskMapper;
        this.cycleMapper = cycleMapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public EvaluationResponse get(Long id) {
        Evaluation e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Evaluation 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<EvaluationResponse> list(long page, long size) {
        IPage<Evaluation> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<EvaluationResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public EvaluationResponse create(EvaluationCreateRequest req) {
        ensureCycleWritable(req.taskId());
        Evaluation e = new Evaluation();
        e.setTaskId(req.taskId());
        e.setEvaluatorId(req.evaluatorId());
        e.setScores(req.scores());
        e.setComment(req.comment());
        mapper.insert(e);
        return toResponse(e);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public EvaluationResponse update(Long id, EvaluationUpdateRequest req) {
        Evaluation e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Evaluation 不存在");
        }
        // 原任务与新任务都要锁定检查（防止把评价挂到已锁周期的任务上）
        ensureCycleWritable(e.getTaskId());
        if (req.taskId() != null) {
            ensureCycleWritable(req.taskId());
        }
        e.setTaskId(req.taskId());
        e.setEvaluatorId(req.evaluatorId());
        e.setScores(req.scores());
        e.setComment(req.comment());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Evaluation e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Evaluation 不存在");
        }
        ensureCycleWritable(e.getTaskId());
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Evaluation 不存在");
        }
    }

    /**
     * 周期锁守卫：按 taskId 定位所属周期，PUBLISHED/CLOSED 时拒绝变更
     * （租户条件由插件注入）。任务或周期缺失时不拦截，存在性由外层语义保证。
     */
    private void ensureCycleWritable(Long taskId) {
        if (taskId == null) {
            return;
        }
        EvalTask task = evalTaskMapper.selectById(taskId);
        if (task == null || task.getCycleId() == null) {
            return;
        }
        Cycle cycle = cycleMapper.selectById(task.getCycleId());
        if (cycle == null) {
            return;
        }
        String status = cycle.getStatus();
        if (CYCLE_PUBLISHED.equals(status) || CYCLE_CLOSED.equals(status)) {
            throw new BizException(ErrorCode.PRF_ERROR,
                    "考核周期已发布或已关闭，禁止变更评价数据");
        }
    }

    private EvaluationResponse toResponse(Evaluation e) {
        return new EvaluationResponse(e.getId(), e.getTaskId(), e.getEvaluatorId(), e.getScores(), e.getComment(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.GoalDtos.GoalBatchItem;
import com.hris.performance.dto.GoalDtos.GoalBatchSaveRequest;
import com.hris.performance.dto.GoalDtos.GoalCreateRequest;
import com.hris.performance.dto.GoalDtos.GoalResponse;
import com.hris.performance.dto.GoalDtos.GoalUpdateRequest;
import com.hris.performance.entity.Cycle;
import com.hris.performance.entity.Goal;
import com.hris.performance.mapper.CycleMapper;
import com.hris.performance.mapper.GoalMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Goal 服务（CRUD + 周期状态守卫 + 权重合计校验）。
 *
 * <p>PRF-F-002：目标仅 DRAFT/ACTIVE 可写，SCORING 起冻结、CLOSED 全锁。
 * PRF-F-003：同周期 + 同员工 + 同维度权重合计口径见 {@link GoalWeightRule}；
 * 批量提交（{@code cycleId:empId:dimension} 整组）先聚合校验再整体替换落库。</p>
 */
@Service
public class GoalService {

    private final GoalMapper mapper;
    private final CycleMapper cycleMapper;

    public GoalService(GoalMapper mapper, CycleMapper cycleMapper) {
        this.mapper = mapper;
        this.cycleMapper = cycleMapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public GoalResponse get(Long id) {
        return toResponse(require(id));
    }

    /** 分页查询记录列表。 */
    public PageResult<GoalResponse> list(long page, long size) {
        IPage<Goal> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<GoalResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建目标：状态守卫 + 权重不超配（增量录入允许合计 < 100）。 */
    @Transactional(rollbackFor = Exception.class)
    public GoalResponse create(GoalCreateRequest req) {
        ensureCycleWritable(req.cycleId());
        String dimension = GoalWeightRule.normalizeDimension(req.dimension());
        GoalWeightRule.requireSingleWeight(req.weight(), req.title());

        BigDecimal existing = GoalWeightRule.sum(weightsOf(req.cycleId(), req.empId(), dimension, null));
        GoalWeightRule.requireNotExceed(existing.add(req.weight()),
                GoalWeightRule.scope(req.cycleId(), req.empId(), dimension));

        Goal e = new Goal();
        e.setCycleId(req.cycleId());
        e.setEmpId(req.empId());
        e.setTitle(req.title());
        e.setDimension(dimension);
        e.setWeight(req.weight());
        e.setTargetValue(req.targetValue());
        e.setResultValue(req.resultValue());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新目标：原周期与新周期都做状态守卫，权重按"剔除自身后的合计 + 新值"校验。 */
    @Transactional(rollbackFor = Exception.class)
    public GoalResponse update(Long id, GoalUpdateRequest req) {
        Goal e = require(id);
        Long cycleId = req.cycleId() == null ? e.getCycleId() : req.cycleId();
        Long empId = req.empId() == null ? e.getEmpId() : req.empId();
        ensureCycleWritable(e.getCycleId());
        ensureCycleWritable(cycleId);

        String dimension = GoalWeightRule.normalizeDimension(
                req.dimension() == null ? e.getDimension() : req.dimension());
        BigDecimal weight = req.weight() == null ? e.getWeight() : req.weight();
        GoalWeightRule.requireSingleWeight(weight, req.title() == null ? e.getTitle() : req.title());

        BigDecimal others = GoalWeightRule.sum(weightsOf(cycleId, empId, dimension, id));
        GoalWeightRule.requireNotExceed(others.add(weight),
                GoalWeightRule.scope(cycleId, empId, dimension));

        e.setCycleId(cycleId);
        e.setEmpId(empId);
        e.setTitle(req.title() == null ? e.getTitle() : req.title());
        e.setDimension(dimension);
        e.setWeight(weight);
        e.setTargetValue(req.targetValue());
        e.setResultValue(req.resultValue());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /**
     * 批量提交（整体替换）：对整组 items 先聚合校验合计 = 100，再删除该组旧目标并写入新集合，
     * 全过程在同一事务内，避免"删了旧的、新的校验失败"导致数据丢失。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<GoalResponse> batchSave(GoalBatchSaveRequest req) {
        ensureCycleWritable(req.cycleId());
        String dimension = GoalWeightRule.normalizeDimension(req.dimension());
        String scope = GoalWeightRule.scope(req.cycleId(), req.empId(), dimension);

        List<GoalBatchItem> items = req.items() == null ? List.of() : req.items();
        List<BigDecimal> weights = new ArrayList<>(items.size());
        List<String> titles = new ArrayList<>(items.size());
        for (GoalBatchItem item : items) {
            if (item == null || item.title() == null || item.title().isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "目标条目不能为空（" + scope + "）");
            }
            weights.add(item.weight());
            titles.add(item.title());
        }
        GoalWeightRule.requireBatchItems(weights, titles, scope);
        GoalWeightRule.requireComplete(GoalWeightRule.sum(weights), scope);

        mapper.delete(new LambdaQueryWrapper<Goal>()
                .eq(Goal::getCycleId, req.cycleId())
                .eq(Goal::getEmpId, req.empId())
                .eq(Goal::getDimension, dimension));

        List<GoalResponse> saved = new ArrayList<>(items.size());
        for (GoalBatchItem item : items) {
            Goal e = new Goal();
            e.setCycleId(req.cycleId());
            e.setEmpId(req.empId());
            e.setTitle(item.title());
            e.setDimension(dimension);
            e.setWeight(item.weight());
            e.setTargetValue(item.targetValue());
            e.setResultValue(item.resultValue());
            mapper.insert(e);
            saved.add(toResponse(e));
        }
        return saved;
    }

    /** 删除目标：状态守卫同写路径。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Goal e = require(id);
        ensureCycleWritable(e.getCycleId());
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Goal 不存在");
        }
    }

    /** 周期状态守卫：目标仅 DRAFT/ACTIVE 可写（租户条件由插件注入）。 */
    private void ensureCycleWritable(Long cycleId) {
        if (cycleId == null) {
            return;
        }
        Cycle cycle = cycleMapper.selectById(cycleId);
        if (cycle == null) {
            return;
        }
        CycleStateMachine.requireGoalWritable(cycle.getStatus());
    }

    /** 取同组权重（excludeId 非空时排除该条，用于更新场景）。 */
    private List<BigDecimal> weightsOf(Long cycleId, Long empId, String dimension, Long excludeId) {
        if (cycleId == null || empId == null) {
            return List.of();
        }
        LambdaQueryWrapper<Goal> w = new LambdaQueryWrapper<Goal>()
                .eq(Goal::getCycleId, cycleId)
                .eq(Goal::getEmpId, empId)
                .eq(Goal::getDimension, dimension);
        if (excludeId != null) {
            w.ne(Goal::getId, excludeId);
        }
        return mapper.selectList(w).stream().map(Goal::getWeight).toList();
    }

    private Goal require(Long id) {
        Goal e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Goal 不存在");
        }
        return e;
    }

    private GoalResponse toResponse(Goal e) {
        return new GoalResponse(e.getId(), e.getCycleId(), e.getEmpId(), e.getTitle(), e.getDimension(), e.getWeight(), e.getTargetValue(), e.getResultValue(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

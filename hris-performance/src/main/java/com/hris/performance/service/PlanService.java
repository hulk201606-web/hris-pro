package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.PlanDtos.PlanCreateRequest;
import com.hris.performance.dto.PlanDtos.PlanResponse;
import com.hris.performance.dto.PlanDtos.PlanUpdateRequest;
import com.hris.performance.entity.Cycle;
import com.hris.performance.entity.Plan;
import com.hris.performance.mapper.CycleMapper;
import com.hris.performance.mapper.PlanMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Plan 服务（CRUD）。
 *
 * <p>周期状态守卫（PRF-F-002）：方案是周期考核规则的模板，被任何非 DRAFT 周期引用后
 * update/delete 一律拒绝（改规则会影响在途考核的权重/流程口径），只有引用方全部处于
 * DRAFT 时才允许调整。create 不受限（新方案尚未被引用）。</p>
 */
@Service
public class PlanService {

    private final PlanMapper mapper;
    private final CycleMapper cycleMapper;

    public PlanService(PlanMapper mapper, CycleMapper cycleMapper) {
        this.mapper = mapper;
        this.cycleMapper = cycleMapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public PlanResponse get(Long id) {
        Plan e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Plan 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<PlanResponse> list(long page, long size) {
        IPage<Plan> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<PlanResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建方案模板（未被引用，无需周期守卫）。 */
    @Transactional(rollbackFor = Exception.class)
    public PlanResponse create(PlanCreateRequest req) {
        Plan e = new Plan();
        e.setName(req.name());
        e.setCode(req.code());
        e.setStatus(req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新方案：被非 DRAFT 周期引用时拒绝。 */
    @Transactional(rollbackFor = Exception.class)
    public PlanResponse update(Long id, PlanUpdateRequest req) {
        Plan e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Plan 不存在");
        }
        ensurePlanWritable(id);
        e.setName(req.name());
        e.setCode(req.code());
        e.setStatus(req.status());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除方案：被非 DRAFT 周期引用时拒绝（保留在途考核的方案快照可追溯）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ensurePlanWritable(id);
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Plan 不存在");
        }
    }

    /** 是否存在引用该方案且已启动（非 DRAFT）的周期。 */
    private void ensurePlanWritable(Long planId) {
        long started = cycleMapper.selectCount(new LambdaQueryWrapper<Cycle>()
                .eq(Cycle::getPlanId, planId)
                .ne(Cycle::getStatus, CycleService.STATUS_DRAFT));
        CycleStateMachine.requirePlanWritable(started > 0);
    }

    private PlanResponse toResponse(Plan e) {
        return new PlanResponse(e.getId(), e.getName(), e.getCode(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

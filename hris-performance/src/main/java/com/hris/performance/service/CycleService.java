package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.CycleDtos.CycleCreateRequest;
import com.hris.performance.dto.CycleDtos.CycleResponse;
import com.hris.performance.dto.CycleDtos.CycleUpdateRequest;
import com.hris.performance.entity.Cycle;
import com.hris.performance.mapper.CycleMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cycle 服务（CRUD + 状态机，PRF-F-002）。
 *
 * <p>状态机：DRAFT→ACTIVE→SCORING→CLOSED，白名单见 {@link CycleStateMachine}。
 * <ul>
 *   <li>创建恒为 DRAFT，调用方不可指定起始态；</li>
 *   <li>update/delete 仅 DRAFT 可执行，且 update 不得夹带状态变更（必须走 transition 端点）；</li>
 *   <li>transition 端点做白名单校验，非法流转 PARAM_INVALID。</li>
 * </ul>
 * 子对象（目标/任务/评分/方案/申诉）的周期锁由各 Service 经 CycleStateMachine 强制。</p>
 */
@Service
public class CycleService {

    /** 状态：配置期 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 状态：启动期（自评窗口） */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态：评分期 */
    public static final String STATUS_SCORING = "SCORING";
    /** 状态：已关闭（封板） */
    public static final String STATUS_CLOSED = "CLOSED";

    private final CycleMapper mapper;

    public CycleService(CycleMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public CycleResponse get(Long id) {
        return toResponse(require(id));
    }

    /** 分页查询记录列表。 */
    public PageResult<CycleResponse> list(long page, long size) {
        IPage<Cycle> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<CycleResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建周期：服务端强制 DRAFT 起始态，调用方传入非 DRAFT 视为参数非法。 */
    @Transactional(rollbackFor = Exception.class)
    public CycleResponse create(CycleCreateRequest req) {
        CycleStatus requested = CycleStatus.of(req.status());
        if (req.status() != null && !req.status().isBlank() && requested != CycleStatus.DRAFT) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "新建周期必须为 DRAFT，后续变更请调用 transition 端点");
        }
        Cycle e = new Cycle();
        e.setPlanId(req.planId());
        e.setPeriodCode(req.periodCode());
        e.setStartDate(req.startDate());
        e.setEndDate(req.endDate());
        e.setStatus(STATUS_DRAFT);
        mapper.insert(e);
        return toResponse(e);
    }

    /**
     * 更新周期配置：仅 DRAFT 可改；status 只能等于当前值（状态迁移走 {@link #transition}），
     * 防止用 update 绕过状态机白名单。
     */
    @Transactional(rollbackFor = Exception.class)
    public CycleResponse update(Long id, CycleUpdateRequest req) {
        Cycle e = require(id);
        CycleStateMachine.requireConfigEditable(e.getStatus());
        String target = req.status();
        if (target != null && !target.isBlank()
                && CycleStatus.of(target) != CycleStatus.resolve(e.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "状态变更必须调用 POST /prf/cycles/{id}/transition（白名单 "
                            + CycleStateMachine.TRANSITION_HINT + "）");
        }
        e.setPlanId(req.planId());
        e.setPeriodCode(req.periodCode());
        e.setStartDate(req.startDate());
        e.setEndDate(req.endDate());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除周期：仅 DRAFT 可删，已启动的周期一律保留（历史与审计）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Cycle e = require(id);
        CycleStateMachine.requireConfigEditable(e.getStatus());
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Cycle 不存在");
        }
    }

    /**
     * 状态流转：白名单校验通过后落库。非法流转（跳级/回退/自转/未知目标）抛 PARAM_INVALID。
     */
    @Transactional(rollbackFor = Exception.class)
    public CycleResponse transition(Long id, String target) {
        Cycle e = require(id);
        CycleStatus next = CycleStateMachine.requireTransition(e.getStatus(), target);
        e.setStatus(next.name());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    private Cycle require(Long id) {
        Cycle e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Cycle 不存在");
        }
        return e;
    }

    private CycleResponse toResponse(Cycle e) {
        return new CycleResponse(e.getId(), e.getPlanId(), e.getPeriodCode(), e.getStartDate(), e.getEndDate(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

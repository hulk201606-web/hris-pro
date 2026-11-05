package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
import com.hris.compensation.dto.PeriodLockDtos.PeriodLockResponse;
import com.hris.compensation.entity.PeriodLock;
import com.hris.compensation.mapper.PeriodLockMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 期间关账服务（cmp_period_lock，CMP-F-020）。
 *
 * <p>语义：期间级独立锁，与 run 级 LOCK 状态解耦——run LOCK 成功不自动锁期间，
 * 两者独立操作（run 锁针对单批次终态，期间锁冻结该 period 全部批次的变更入口）。
 * 历史多锁并存（uk=tenant+period+locked_at），ACTIVE 至多一条由本服务 lock 前置
 * 检查保证。锁时校验「该 period 存在 run」放宽为不校验（空期间也允许预防性关账）。</p>
 */
@Service
public class PeriodLockService {

    /** 状态：关账生效中 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态：已解锁 */
    public static final String STATUS_RELEASED = "RELEASED";

    private final PeriodLockMapper mapper;

    public PeriodLockService(PeriodLockMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 关账：该 period 无 ACTIVE 锁才可锁（重复 → PARAM_INVALID「已关账」）。
     *
     * <p>run 存在性校验放宽：不要求该 period 已有算薪批次，允许预防性关账（报告已注明）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public PeriodLockResponse lock(String periodCode) {
        String period = requirePeriodCode(periodCode);
        if (hasActiveLock(period)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "期间已关账: " + period);
        }
        PeriodLock e = new PeriodLock();
        e.setPeriodCode(period);
        e.setStatus(STATUS_ACTIVE);
        e.setLockedBy(AuthContextHolder.getUserId());
        e.setLockedAt(LocalDateTime.now());
        mapper.insert(e);
        return toResponse(e);
    }

    /**
     * 解锁：ACTIVE 锁置 RELEASED 并记录 released_by/at/reason（理由必填 ≥4 字）。
     */
    @Transactional(rollbackFor = Exception.class)
    public PeriodLockResponse unlock(String periodCode, String reason) {
        String period = requirePeriodCode(periodCode);
        if (reason == null || reason.trim().length() < 4) {
            throw new BizException(ErrorCode.PARAM_INVALID, "解锁理由至少 4 个字符");
        }
        PeriodLock active = mapper.selectOne(new LambdaQueryWrapper<PeriodLock>()
                .eq(PeriodLock::getPeriodCode, period)
                .eq(PeriodLock::getStatus, STATUS_ACTIVE)
                .last("limit 1"));
        if (active == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该期间无生效的关账锁: " + period);
        }
        active.setStatus(STATUS_RELEASED);
        active.setReleasedBy(AuthContextHolder.getUserId());
        active.setReleasedAt(LocalDateTime.now());
        active.setUnlockReason(reason.trim());
        if (mapper.updateById(active) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(active);
    }

    /** 分页查询（active=true 只看生效锁，periodCode 可选过滤），按关账时间倒序。 */
    public PageResult<PeriodLockResponse> list(Boolean active, String periodCode, long page, long size) {
        LambdaQueryWrapper<PeriodLock> qw = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(active)) {
            qw.eq(PeriodLock::getStatus, STATUS_ACTIVE);
        }
        if (periodCode != null && !periodCode.isBlank()) {
            qw.eq(PeriodLock::getPeriodCode, periodCode.trim());
        }
        qw.orderByDesc(PeriodLock::getLockedAt);
        IPage<PeriodLock> pg = mapper.selectPage(new Page<>(page, size), qw);
        return new PageResult<>(pg.getRecords().stream().map(this::toResponse).toList(), page, size, pg.getTotal());
    }

    /**
     * 期间级变更守卫：该 period 存在 ACTIVE 锁则拒绝（PARAM_INVALID「期间已关账，禁止变更」）。
     * 供 PayrollRunService 的 create/update/delete/submit 路径调用。
     */
    public void ensurePeriodUnlocked(String periodCode) {
        if (periodCode != null && hasActiveLock(periodCode)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "期间已关账，禁止变更: " + periodCode);
        }
    }

    private boolean hasActiveLock(String period) {
        return mapper.selectCount(new LambdaQueryWrapper<PeriodLock>()
                .eq(PeriodLock::getPeriodCode, period)
                .eq(PeriodLock::getStatus, STATUS_ACTIVE)) > 0;
    }

    private String requirePeriodCode(String periodCode) {
        if (periodCode == null || periodCode.isBlank() || periodCode.length() > 16) {
            throw new BizException(ErrorCode.PARAM_INVALID, "periodCode 不能为空且不超过 16 字符");
        }
        return periodCode.trim();
    }

    private PeriodLockResponse toResponse(PeriodLock e) {
        return new PeriodLockResponse(e.getId(), e.getPeriodCode(), e.getStatus(),
                e.getLockedBy(), e.getLockedAt(),
                e.getReleasedBy(), e.getReleasedAt(), e.getUnlockReason());
    }
}

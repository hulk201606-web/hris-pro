package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.compensation.dto.PayrollAdjustmentDtos.PayrollAdjustmentCreateRequest;
import com.hris.compensation.dto.PayrollAdjustmentDtos.PayrollAdjustmentResponse;
import com.hris.compensation.dto.PayrollAdjustmentDtos.PayrollAdjustmentUpdateRequest;
import com.hris.compensation.entity.PayrollAdjustment;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.PayrollAdjustmentMapper;
import com.hris.compensation.mapper.PayrollRunMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PayrollAdjustment 服务（CRUD + CMP-F-020 关账后调整通道约束）。
 *
 * <p>通道语义：期间 ACTIVE 关账后，常规 run 变更已被拒绝（旧批已做），调整单是唯一补差入口，
 * 但只允许指向<b>下一期间</b>（run.periodCode 严格大于锁定期间）——锁定期间的调整单不可改金额，
 * 只能新建下期调整单，违规 PARAM_INVALID 附原因。</p>
 */
@Service
public class PayrollAdjustmentService {

    private final PayrollAdjustmentMapper mapper;
    private final PayrollRunMapper runMapper;
    private final PeriodLockService periodLockService;

    public PayrollAdjustmentService(PayrollAdjustmentMapper mapper, PayrollRunMapper runMapper,
                                    PeriodLockService periodLockService) {
        this.mapper = mapper;
        this.runMapper = runMapper;
        this.periodLockService = periodLockService;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public PayrollAdjustmentResponse get(Long id) {
        PayrollAdjustment e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollAdjustment 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<PayrollAdjustmentResponse> list(long page, long size) {
        IPage<PayrollAdjustment> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<PayrollAdjustmentResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录：目标批次期间必须满足关账后调整通道约束。 */
    @Transactional(rollbackFor = Exception.class)
    public PayrollAdjustmentResponse create(PayrollAdjustmentCreateRequest req) {
        PayrollRun run = requireRun(req.runId());
        periodLockService.ensureAdjustmentChannelAllowed(run.getPeriodCode());
        PayrollAdjustment e = new PayrollAdjustment();
        e.setRunId(req.runId());
        e.setEmpId(req.empId());
        e.setAdjustType(req.adjustType());
        e.setAmount(req.amount());
        e.setReason(req.reason());
        mapper.insert(e);
        return toResponse(e);
    }

    /**
     * 更新记录：锁定期间的既有调整单不可改金额（只能新建下一期间调整单）；
     * 目标批次期间同样须满足调整通道约束。
     */
    @Transactional(rollbackFor = Exception.class)
    public PayrollAdjustmentResponse update(Long id, PayrollAdjustmentUpdateRequest req) {
        PayrollAdjustment e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollAdjustment 不存在");
        }
        PayrollRun currentRun = requireRun(e.getRunId());
        if (periodLockService.isPeriodLocked(currentRun.getPeriodCode())
                && amountChanged(e.getAmount(), req.amount())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "期间 " + currentRun.getPeriodCode() + " 已关账，该调整单不可改金额，"
                            + "请新建下一期间调整单");
        }
        Long targetRunId = req.runId() != null ? req.runId() : e.getRunId();
        PayrollRun targetRun = requireRun(targetRunId);
        periodLockService.ensureAdjustmentChannelAllowed(targetRun.getPeriodCode());
        e.setRunId(targetRunId);
        e.setEmpId(req.empId());
        e.setAdjustType(req.adjustType());
        e.setAmount(req.amount());
        e.setReason(req.reason());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollAdjustment 不存在");
        }
    }

    private boolean amountChanged(java.math.BigDecimal current, java.math.BigDecimal target) {
        if (current == null || target == null) {
            return current != target;
        }
        return current.compareTo(target) != 0;
    }

    private PayrollRun requireRun(Long runId) {
        if (runId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "runId 不能为空");
        }
        PayrollRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
        return run;
    }

    private PayrollAdjustmentResponse toResponse(PayrollAdjustment e) {
        return new PayrollAdjustmentResponse(e.getId(), e.getRunId(), e.getEmpId(), e.getAdjustType(), e.getAmount(), e.getReason(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.compensation.dto.PayrollAdjustmentDtos.PayrollAdjustmentCreateRequest;
import com.hris.compensation.dto.PayrollAdjustmentDtos.PayrollAdjustmentResponse;
import com.hris.compensation.dto.PayrollAdjustmentDtos.PayrollAdjustmentUpdateRequest;
import com.hris.compensation.entity.PayrollAdjustment;
import com.hris.compensation.mapper.PayrollAdjustmentMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PayrollAdjustment 服务（CRUD）。
 */
@Service
public class PayrollAdjustmentService {

    private final PayrollAdjustmentMapper mapper;

    public PayrollAdjustmentService(PayrollAdjustmentMapper mapper) {
        this.mapper = mapper;
    }

    public PayrollAdjustmentResponse get(Long id) {
        PayrollAdjustment e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollAdjustment 不存在");
        }
        return toResponse(e);
    }

    public PageResult<PayrollAdjustmentResponse> list(long page, long size) {
        IPage<PayrollAdjustment> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<PayrollAdjustmentResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public PayrollAdjustmentResponse create(PayrollAdjustmentCreateRequest req) {
        PayrollAdjustment e = new PayrollAdjustment();
        e.setRunId(req.runId());
        e.setEmpId(req.empId());
        e.setAdjustType(req.adjustType());
        e.setAmount(req.amount());
        e.setReason(req.reason());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public PayrollAdjustmentResponse update(Long id, PayrollAdjustmentUpdateRequest req) {
        PayrollAdjustment e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollAdjustment 不存在");
        }
        e.setRunId(req.runId());
        e.setEmpId(req.empId());
        e.setAdjustType(req.adjustType());
        e.setAmount(req.amount());
        e.setReason(req.reason());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollAdjustment 不存在");
        }
    }

    private PayrollAdjustmentResponse toResponse(PayrollAdjustment e) {
        return new PayrollAdjustmentResponse(e.getId(), e.getRunId(), e.getEmpId(), e.getAdjustType(), e.getAmount(), e.getReason(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

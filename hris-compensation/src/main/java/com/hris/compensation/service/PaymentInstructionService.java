package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.compensation.dto.PaymentInstructionDtos.PaymentInstructionCreateRequest;
import com.hris.compensation.dto.PaymentInstructionDtos.PaymentInstructionResponse;
import com.hris.compensation.dto.PaymentInstructionDtos.PaymentInstructionUpdateRequest;
import com.hris.compensation.entity.PaymentInstruction;
import com.hris.compensation.mapper.PaymentInstructionMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PaymentInstruction 服务（CRUD）。
 */
@Service
public class PaymentInstructionService {

    private final PaymentInstructionMapper mapper;

    public PaymentInstructionService(PaymentInstructionMapper mapper) {
        this.mapper = mapper;
    }

    public PaymentInstructionResponse get(Long id) {
        PaymentInstruction e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PaymentInstruction 不存在");
        }
        return toResponse(e);
    }

    public PageResult<PaymentInstructionResponse> list(long page, long size) {
        IPage<PaymentInstruction> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<PaymentInstructionResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentInstructionResponse create(PaymentInstructionCreateRequest req) {
        PaymentInstruction e = new PaymentInstruction();
        e.setRunId(req.runId());
        e.setEmpId(req.empId());
        e.setPayStatus(req.payStatus());
        e.setPaidAt(req.paidAt());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentInstructionResponse update(Long id, PaymentInstructionUpdateRequest req) {
        PaymentInstruction e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PaymentInstruction 不存在");
        }
        e.setRunId(req.runId());
        e.setEmpId(req.empId());
        e.setPayStatus(req.payStatus());
        e.setPaidAt(req.paidAt());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "PaymentInstruction 不存在");
        }
    }

    private PaymentInstructionResponse toResponse(PaymentInstruction e) {
        return new PaymentInstructionResponse(e.getId(), e.getRunId(), e.getEmpId(), e.getPayStatus(), e.getPaidAt(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

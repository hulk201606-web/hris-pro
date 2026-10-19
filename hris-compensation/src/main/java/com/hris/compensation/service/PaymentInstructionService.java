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

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public PaymentInstructionResponse get(Long id) {
        PaymentInstruction e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PaymentInstruction 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<PaymentInstructionResponse> list(long page, long size) {
        IPage<PaymentInstruction> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<PaymentInstructionResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

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
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

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
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

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

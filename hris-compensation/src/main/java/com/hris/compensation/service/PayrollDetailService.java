package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.compensation.dto.PayrollDetailDtos.PayrollDetailCreateRequest;
import com.hris.compensation.dto.PayrollDetailDtos.PayrollDetailResponse;
import com.hris.compensation.dto.PayrollDetailDtos.PayrollDetailUpdateRequest;
import com.hris.compensation.entity.PayrollDetail;
import com.hris.compensation.mapper.PayrollDetailMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PayrollDetail 服务（CRUD）。
 */
@Service
public class PayrollDetailService {

    private final PayrollDetailMapper mapper;

    public PayrollDetailService(PayrollDetailMapper mapper) {
        this.mapper = mapper;
    }

    public PayrollDetailResponse get(Long id) {
        PayrollDetail e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollDetail 不存在");
        }
        return toResponse(e);
    }

    public PageResult<PayrollDetailResponse> list(long page, long size) {
        IPage<PayrollDetail> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<PayrollDetailResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public PayrollDetailResponse create(PayrollDetailCreateRequest req) {
        PayrollDetail e = new PayrollDetail();
        e.setRunId(req.runId());
        e.setEmpId(req.empId());
        e.setInputSnapshot(req.inputSnapshot());
        e.setAmountCipher(req.amountCipher());
        e.setAmountMask(req.amountMask());
        e.setNetAmountCipher(req.netAmountCipher());
        e.setNetAmountMask(req.netAmountMask());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public PayrollDetailResponse update(Long id, PayrollDetailUpdateRequest req) {
        PayrollDetail e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollDetail 不存在");
        }
        e.setRunId(req.runId());
        e.setEmpId(req.empId());
        e.setInputSnapshot(req.inputSnapshot());
        e.setAmountCipher(req.amountCipher());
        e.setAmountMask(req.amountMask());
        e.setNetAmountCipher(req.netAmountCipher());
        e.setNetAmountMask(req.netAmountMask());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollDetail 不存在");
        }
    }

    private PayrollDetailResponse toResponse(PayrollDetail e) {
        // 密域下沉：amountCipher/netAmountCipher 不随通用响应暴露，仅掩码；密文走 SalarySecretService
        return new PayrollDetailResponse(e.getId(), e.getRunId(), e.getEmpId(), e.getInputSnapshot(), e.getAmountMask(), e.getNetAmountMask(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

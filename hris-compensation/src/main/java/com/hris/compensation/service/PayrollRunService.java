package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunCreateRequest;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunResponse;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunUpdateRequest;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.PayrollRunMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PayrollRun 服务（CRUD）。
 */
@Service
public class PayrollRunService {

    private final PayrollRunMapper mapper;

    public PayrollRunService(PayrollRunMapper mapper) {
        this.mapper = mapper;
    }

    public PayrollRunResponse get(Long id) {
        PayrollRun e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
        return toResponse(e);
    }

    public PageResult<PayrollRunResponse> list(long page, long size) {
        IPage<PayrollRun> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<PayrollRunResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public PayrollRunResponse create(PayrollRunCreateRequest req) {
        PayrollRun e = new PayrollRun();
        e.setPeriodCode(req.periodCode());
        e.setPayGroupId(req.payGroupId());
        e.setStatus(req.status());
        e.setCalcBy(req.calcBy());
        e.setReviewBy(req.reviewBy());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public PayrollRunResponse update(Long id, PayrollRunUpdateRequest req) {
        PayrollRun e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
        e.setPeriodCode(req.periodCode());
        e.setPayGroupId(req.payGroupId());
        e.setStatus(req.status());
        e.setCalcBy(req.calcBy());
        e.setReviewBy(req.reviewBy());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
    }

    private PayrollRunResponse toResponse(PayrollRun e) {
        return new PayrollRunResponse(e.getId(), e.getPeriodCode(), e.getPayGroupId(), e.getStatus(), e.getCalcBy(), e.getReviewBy(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

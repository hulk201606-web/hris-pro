package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.compensation.dto.TaxDeductionDtos.TaxDeductionCreateRequest;
import com.hris.compensation.dto.TaxDeductionDtos.TaxDeductionResponse;
import com.hris.compensation.dto.TaxDeductionDtos.TaxDeductionUpdateRequest;
import com.hris.compensation.entity.TaxDeduction;
import com.hris.compensation.mapper.TaxDeductionMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TaxDeduction 服务（CRUD）。
 */
@Service
public class TaxDeductionService {

    private final TaxDeductionMapper mapper;

    public TaxDeductionService(TaxDeductionMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public TaxDeductionResponse get(Long id) {
        TaxDeduction e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "TaxDeduction 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<TaxDeductionResponse> list(long page, long size) {
        IPage<TaxDeduction> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<TaxDeductionResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public TaxDeductionResponse create(TaxDeductionCreateRequest req) {
        TaxDeduction e = new TaxDeduction();
        e.setEmpId(req.empId());
        e.setEffectiveFrom(req.effectiveFrom());
        e.setEffectiveTo(req.effectiveTo());
        e.setDeductionAmountCipher(req.deductionAmountCipher());
        e.setDeductionAmountMask(req.deductionAmountMask());
        mapper.insert(e);
        return toResponse(e);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public TaxDeductionResponse update(Long id, TaxDeductionUpdateRequest req) {
        TaxDeduction e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "TaxDeduction 不存在");
        }
        e.setEmpId(req.empId());
        e.setEffectiveFrom(req.effectiveFrom());
        e.setEffectiveTo(req.effectiveTo());
        e.setDeductionAmountCipher(req.deductionAmountCipher());
        e.setDeductionAmountMask(req.deductionAmountMask());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "TaxDeduction 不存在");
        }
    }

    private TaxDeductionResponse toResponse(TaxDeduction e) {
        // 密域下沉：deductionAmountCipher 不随通用响应暴露，仅掩码；密文走 SalarySecretService
        return new TaxDeductionResponse(e.getId(), e.getEmpId(), e.getEffectiveFrom(), e.getEffectiveTo(), e.getDeductionAmountMask(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.recruit.dto.OfferDtos.OfferCreateRequest;
import com.hris.recruit.dto.OfferDtos.OfferResponse;
import com.hris.recruit.dto.OfferDtos.OfferUpdateRequest;
import com.hris.recruit.entity.Offer;
import com.hris.recruit.mapper.OfferMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 录用意向（Offer）：向候选人发出的录用意向单，含薪酬带宽校验结果与薪酬密文/掩码，状态驱动入职建档联动。
 *
 * <p>业务域：招聘流程（recruit）；分层：Service。</p>
 */
@Service
public class OfferService {

    private final OfferMapper mapper;

    public OfferService(OfferMapper mapper) {
        this.mapper = mapper;
    }

    /** 按 ID 查询，不存在抛 NOT_FOUND。 */
    public OfferResponse get(Long id) {
        Offer e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Offer 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询列表。 */
    public PageResult<OfferResponse> list(long page, long size) {
        IPage<Offer> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<OfferResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建记录（事务）。 */
    @Transactional(rollbackFor = Exception.class)
    public OfferResponse create(OfferCreateRequest req) {
        Offer e = new Offer();
        e.setApplicationId(req.applicationId());
        e.setOfferNo(req.offerNo());
        e.setSalaryBandCheck(req.salaryBandCheck());
        e.setSalaryAmountCipher(req.salaryAmountCipher());
        e.setSalaryAmountMask(req.salaryAmountMask());
        e.setStatus(req.status());
        e.setProcInstId(req.procInstId());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新记录（事务）；数据被并发变更时抛 RCS_ERROR。 */
    @Transactional(rollbackFor = Exception.class)
    public OfferResponse update(Long id, OfferUpdateRequest req) {
        Offer e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Offer 不存在");
        }
        e.setApplicationId(req.applicationId());
        e.setOfferNo(req.offerNo());
        e.setSalaryBandCheck(req.salaryBandCheck());
        e.setSalaryAmountCipher(req.salaryAmountCipher());
        e.setSalaryAmountMask(req.salaryAmountMask());
        e.setStatus(req.status());
        e.setProcInstId(req.procInstId());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除记录（事务）；不存在抛 NOT_FOUND。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Offer 不存在");
        }
    }

    private OfferResponse toResponse(Offer e) {
        return new OfferResponse(e.getId(), e.getApplicationId(), e.getOfferNo(), e.getSalaryBandCheck(), e.getSalaryAmountCipher(), e.getSalaryAmountMask(), e.getStatus(), e.getProcInstId(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

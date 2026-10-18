package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.compensation.dto.SocialFundProfileDtos.SocialFundProfileCreateRequest;
import com.hris.compensation.dto.SocialFundProfileDtos.SocialFundProfileResponse;
import com.hris.compensation.dto.SocialFundProfileDtos.SocialFundProfileUpdateRequest;
import com.hris.compensation.entity.SocialFundProfile;
import com.hris.compensation.mapper.SocialFundProfileMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SocialFundProfile 服务（CRUD）。
 */
@Service
public class SocialFundProfileService {

    private final SocialFundProfileMapper mapper;

    public SocialFundProfileService(SocialFundProfileMapper mapper) {
        this.mapper = mapper;
    }

    public SocialFundProfileResponse get(Long id) {
        SocialFundProfile e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "SocialFundProfile 不存在");
        }
        return toResponse(e);
    }

    public PageResult<SocialFundProfileResponse> list(long page, long size) {
        IPage<SocialFundProfile> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<SocialFundProfileResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public SocialFundProfileResponse create(SocialFundProfileCreateRequest req) {
        SocialFundProfile e = new SocialFundProfile();
        e.setEmpId(req.empId());
        e.setEffectiveFrom(req.effectiveFrom());
        e.setEffectiveTo(req.effectiveTo());
        e.setFundType(req.fundType());
        e.setBaseAmount(req.baseAmount());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public SocialFundProfileResponse update(Long id, SocialFundProfileUpdateRequest req) {
        SocialFundProfile e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "SocialFundProfile 不存在");
        }
        e.setEmpId(req.empId());
        e.setEffectiveFrom(req.effectiveFrom());
        e.setEffectiveTo(req.effectiveTo());
        e.setFundType(req.fundType());
        e.setBaseAmount(req.baseAmount());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "SocialFundProfile 不存在");
        }
    }

    private SocialFundProfileResponse toResponse(SocialFundProfile e) {
        return new SocialFundProfileResponse(e.getId(), e.getEmpId(), e.getEffectiveFrom(), e.getEffectiveTo(), e.getFundType(), e.getBaseAmount(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

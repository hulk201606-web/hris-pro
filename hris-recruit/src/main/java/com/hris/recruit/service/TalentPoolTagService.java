package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.recruit.dto.TalentPoolTagDtos.TalentPoolTagCreateRequest;
import com.hris.recruit.dto.TalentPoolTagDtos.TalentPoolTagResponse;
import com.hris.recruit.dto.TalentPoolTagDtos.TalentPoolTagUpdateRequest;
import com.hris.recruit.entity.TalentPoolTag;
import com.hris.recruit.mapper.TalentPoolTagMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TalentPoolTag 服务（CRUD）。
 */
@Service
public class TalentPoolTagService {

    private final TalentPoolTagMapper mapper;

    public TalentPoolTagService(TalentPoolTagMapper mapper) {
        this.mapper = mapper;
    }

    public TalentPoolTagResponse get(Long id) {
        TalentPoolTag e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "TalentPoolTag 不存在");
        }
        return toResponse(e);
    }

    public PageResult<TalentPoolTagResponse> list(long page, long size) {
        IPage<TalentPoolTag> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<TalentPoolTagResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public TalentPoolTagResponse create(TalentPoolTagCreateRequest req) {
        TalentPoolTag e = new TalentPoolTag();
        e.setCandidateId(req.candidateId());
        e.setTag(req.tag());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public TalentPoolTagResponse update(Long id, TalentPoolTagUpdateRequest req) {
        TalentPoolTag e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "TalentPoolTag 不存在");
        }
        e.setCandidateId(req.candidateId());
        e.setTag(req.tag());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "TalentPoolTag 不存在");
        }
    }

    private TalentPoolTagResponse toResponse(TalentPoolTag e) {
        return new TalentPoolTagResponse(e.getId(), e.getCandidateId(), e.getTag(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

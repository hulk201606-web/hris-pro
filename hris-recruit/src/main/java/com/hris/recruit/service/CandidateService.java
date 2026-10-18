package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.recruit.dto.CandidateDtos.CandidateCreateRequest;
import com.hris.recruit.dto.CandidateDtos.CandidateResponse;
import com.hris.recruit.dto.CandidateDtos.CandidateUpdateRequest;
import com.hris.recruit.entity.Candidate;
import com.hris.recruit.mapper.CandidateMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Candidate 服务（CRUD）。
 */
@Service
public class CandidateService {

    private final CandidateMapper mapper;

    public CandidateService(CandidateMapper mapper) {
        this.mapper = mapper;
    }

    public CandidateResponse get(Long id) {
        Candidate e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Candidate 不存在");
        }
        return toResponse(e);
    }

    public PageResult<CandidateResponse> list(long page, long size) {
        IPage<Candidate> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<CandidateResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public CandidateResponse create(CandidateCreateRequest req) {
        Candidate e = new Candidate();
        e.setName(req.name());
        e.setMobileCipher(req.mobileCipher());
        e.setMobileMask(req.mobileMask());
        e.setEmail(req.email());
        e.setStatus(req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public CandidateResponse update(Long id, CandidateUpdateRequest req) {
        Candidate e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Candidate 不存在");
        }
        e.setName(req.name());
        e.setMobileCipher(req.mobileCipher());
        e.setMobileMask(req.mobileMask());
        e.setEmail(req.email());
        e.setStatus(req.status());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Candidate 不存在");
        }
    }

    private CandidateResponse toResponse(Candidate e) {
        return new CandidateResponse(e.getId(), e.getName(), e.getMobileCipher(), e.getMobileMask(), e.getEmail(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

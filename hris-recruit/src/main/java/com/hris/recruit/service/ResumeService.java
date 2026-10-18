package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.recruit.dto.ResumeDtos.ResumeCreateRequest;
import com.hris.recruit.dto.ResumeDtos.ResumeResponse;
import com.hris.recruit.dto.ResumeDtos.ResumeUpdateRequest;
import com.hris.recruit.entity.Resume;
import com.hris.recruit.mapper.ResumeMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Resume 服务（CRUD）。
 */
@Service
public class ResumeService {

    private final ResumeMapper mapper;

    public ResumeService(ResumeMapper mapper) {
        this.mapper = mapper;
    }

    public ResumeResponse get(Long id) {
        Resume e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Resume 不存在");
        }
        return toResponse(e);
    }

    public PageResult<ResumeResponse> list(long page, long size) {
        IPage<Resume> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<ResumeResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public ResumeResponse create(ResumeCreateRequest req) {
        Resume e = new Resume();
        e.setCandidateId(req.candidateId());
        e.setFileId(req.fileId());
        e.setParseResult(req.parseResult());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResumeResponse update(Long id, ResumeUpdateRequest req) {
        Resume e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Resume 不存在");
        }
        e.setCandidateId(req.candidateId());
        e.setFileId(req.fileId());
        e.setParseResult(req.parseResult());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Resume 不存在");
        }
    }

    private ResumeResponse toResponse(Resume e) {
        return new ResumeResponse(e.getId(), e.getCandidateId(), e.getFileId(), e.getParseResult(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

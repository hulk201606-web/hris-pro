package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.recruit.dto.InterviewDtos.InterviewCreateRequest;
import com.hris.recruit.dto.InterviewDtos.InterviewResponse;
import com.hris.recruit.dto.InterviewDtos.InterviewUpdateRequest;
import com.hris.recruit.entity.Interview;
import com.hris.recruit.mapper.InterviewMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Interview 服务（CRUD）。
 */
@Service
public class InterviewService {

    private final InterviewMapper mapper;

    public InterviewService(InterviewMapper mapper) {
        this.mapper = mapper;
    }

    public InterviewResponse get(Long id) {
        Interview e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Interview 不存在");
        }
        return toResponse(e);
    }

    public PageResult<InterviewResponse> list(long page, long size) {
        IPage<Interview> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<InterviewResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public InterviewResponse create(InterviewCreateRequest req) {
        Interview e = new Interview();
        e.setApplicationId(req.applicationId());
        e.setRound(req.round());
        e.setPlannedAt(req.plannedAt());
        e.setInterviewerIds(req.interviewerIds());
        e.setStatus(req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public InterviewResponse update(Long id, InterviewUpdateRequest req) {
        Interview e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Interview 不存在");
        }
        e.setApplicationId(req.applicationId());
        e.setRound(req.round());
        e.setPlannedAt(req.plannedAt());
        e.setInterviewerIds(req.interviewerIds());
        e.setStatus(req.status());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Interview 不存在");
        }
    }

    private InterviewResponse toResponse(Interview e) {
        return new InterviewResponse(e.getId(), e.getApplicationId(), e.getRound(), e.getPlannedAt(), e.getInterviewerIds(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

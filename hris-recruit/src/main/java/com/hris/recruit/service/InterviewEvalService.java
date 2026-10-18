package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.recruit.dto.InterviewEvalDtos.InterviewEvalCreateRequest;
import com.hris.recruit.dto.InterviewEvalDtos.InterviewEvalResponse;
import com.hris.recruit.dto.InterviewEvalDtos.InterviewEvalUpdateRequest;
import com.hris.recruit.entity.InterviewEval;
import com.hris.recruit.mapper.InterviewEvalMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * InterviewEval 服务（CRUD）。
 */
@Service
public class InterviewEvalService {

    private final InterviewEvalMapper mapper;

    public InterviewEvalService(InterviewEvalMapper mapper) {
        this.mapper = mapper;
    }

    public InterviewEvalResponse get(Long id) {
        InterviewEval e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "InterviewEval 不存在");
        }
        return toResponse(e);
    }

    public PageResult<InterviewEvalResponse> list(long page, long size) {
        IPage<InterviewEval> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<InterviewEvalResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public InterviewEvalResponse create(InterviewEvalCreateRequest req) {
        InterviewEval e = new InterviewEval();
        e.setInterviewId(req.interviewId());
        e.setEvaluatorId(req.evaluatorId());
        e.setScore(req.score());
        e.setComment(req.comment());
        e.setPass(req.pass());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public InterviewEvalResponse update(Long id, InterviewEvalUpdateRequest req) {
        InterviewEval e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "InterviewEval 不存在");
        }
        e.setInterviewId(req.interviewId());
        e.setEvaluatorId(req.evaluatorId());
        e.setScore(req.score());
        e.setComment(req.comment());
        e.setPass(req.pass());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "InterviewEval 不存在");
        }
    }

    private InterviewEvalResponse toResponse(InterviewEval e) {
        return new InterviewEvalResponse(e.getId(), e.getInterviewId(), e.getEvaluatorId(), e.getScore(), e.getComment(), e.getPass(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

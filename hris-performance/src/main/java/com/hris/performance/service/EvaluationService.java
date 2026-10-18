package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.EvaluationDtos.EvaluationCreateRequest;
import com.hris.performance.dto.EvaluationDtos.EvaluationResponse;
import com.hris.performance.dto.EvaluationDtos.EvaluationUpdateRequest;
import com.hris.performance.entity.Evaluation;
import com.hris.performance.mapper.EvaluationMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Evaluation 服务（CRUD）。
 */
@Service
public class EvaluationService {

    private final EvaluationMapper mapper;

    public EvaluationService(EvaluationMapper mapper) {
        this.mapper = mapper;
    }

    public EvaluationResponse get(Long id) {
        Evaluation e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Evaluation 不存在");
        }
        return toResponse(e);
    }

    public PageResult<EvaluationResponse> list(long page, long size) {
        IPage<Evaluation> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<EvaluationResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public EvaluationResponse create(EvaluationCreateRequest req) {
        Evaluation e = new Evaluation();
        e.setTaskId(req.taskId());
        e.setEvaluatorId(req.evaluatorId());
        e.setScores(req.scores());
        e.setComment(req.comment());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public EvaluationResponse update(Long id, EvaluationUpdateRequest req) {
        Evaluation e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Evaluation 不存在");
        }
        e.setTaskId(req.taskId());
        e.setEvaluatorId(req.evaluatorId());
        e.setScores(req.scores());
        e.setComment(req.comment());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Evaluation 不存在");
        }
    }

    private EvaluationResponse toResponse(Evaluation e) {
        return new EvaluationResponse(e.getId(), e.getTaskId(), e.getEvaluatorId(), e.getScores(), e.getComment(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

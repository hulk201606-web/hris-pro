package com.hris.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.rag.dto.EvalCaseDtos.EvalCaseCreateRequest;
import com.hris.rag.dto.EvalCaseDtos.EvalCaseResponse;
import com.hris.rag.dto.EvalCaseDtos.EvalCaseUpdateRequest;
import com.hris.rag.entity.EvalCase;
import com.hris.rag.mapper.EvalCaseMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * EvalCase 服务（CRUD）。
 */
@Service
public class EvalCaseService {

    private final EvalCaseMapper mapper;

    public EvalCaseService(EvalCaseMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询评测用例详情，不存在则抛 NOT_FOUND。 */
    public EvalCaseResponse get(Long id) {
        EvalCase e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "EvalCase 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询评测用例列表。 */
    public PageResult<EvalCaseResponse> list(long page, long size) {
        IPage<EvalCase> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<EvalCaseResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建评测用例。 */
    @Transactional(rollbackFor = Exception.class)
    public EvalCaseResponse create(EvalCaseCreateRequest req) {
        EvalCase e = new EvalCase();
        e.setEvalSetId(req.evalSetId());
        e.setQuestion(req.question());
        e.setExpectedRefs(req.expectedRefs());
        e.setGroundTruth(req.groundTruth());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新评测用例（乐观锁防并发覆盖）。 */
    @Transactional(rollbackFor = Exception.class)
    public EvalCaseResponse update(Long id, EvalCaseUpdateRequest req) {
        EvalCase e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "EvalCase 不存在");
        }
        e.setEvalSetId(req.evalSetId());
        e.setQuestion(req.question());
        e.setExpectedRefs(req.expectedRefs());
        e.setGroundTruth(req.groundTruth());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除评测用例（逻辑删除）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "EvalCase 不存在");
        }
    }

    private EvalCaseResponse toResponse(EvalCase e) {
        return new EvalCaseResponse(e.getId(), e.getEvalSetId(), e.getQuestion(), e.getExpectedRefs(), e.getGroundTruth(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

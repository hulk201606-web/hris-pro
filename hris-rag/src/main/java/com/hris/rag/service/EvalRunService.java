package com.hris.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.rag.dto.EvalRunDtos.EvalRunCreateRequest;
import com.hris.rag.dto.EvalRunDtos.EvalRunResponse;
import com.hris.rag.dto.EvalRunDtos.EvalRunUpdateRequest;
import com.hris.rag.entity.EvalRun;
import com.hris.rag.mapper.EvalRunMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * EvalRun 服务（CRUD）。
 */
@Service
public class EvalRunService {

    private final EvalRunMapper mapper;

    public EvalRunService(EvalRunMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询评测运行记录详情，不存在则抛 NOT_FOUND。 */
    public EvalRunResponse get(Long id) {
        EvalRun e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "EvalRun 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询评测运行记录列表。 */
    public PageResult<EvalRunResponse> list(long page, long size) {
        IPage<EvalRun> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<EvalRunResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建评测运行记录。 */
    @Transactional(rollbackFor = Exception.class)
    public EvalRunResponse create(EvalRunCreateRequest req) {
        EvalRun e = new EvalRun();
        e.setEvalSetId(req.evalSetId());
        e.setMetrics(req.metrics());
        e.setStatus(req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新评测运行记录（乐观锁防并发覆盖）。 */
    @Transactional(rollbackFor = Exception.class)
    public EvalRunResponse update(Long id, EvalRunUpdateRequest req) {
        EvalRun e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "EvalRun 不存在");
        }
        e.setEvalSetId(req.evalSetId());
        e.setMetrics(req.metrics());
        e.setStatus(req.status());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除评测运行记录（逻辑删除）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "EvalRun 不存在");
        }
    }

    private EvalRunResponse toResponse(EvalRun e) {
        return new EvalRunResponse(e.getId(), e.getEvalSetId(), e.getMetrics(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

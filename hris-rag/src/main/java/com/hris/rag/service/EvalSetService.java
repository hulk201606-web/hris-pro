package com.hris.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.rag.dto.EvalSetDtos.EvalSetCreateRequest;
import com.hris.rag.dto.EvalSetDtos.EvalSetResponse;
import com.hris.rag.dto.EvalSetDtos.EvalSetUpdateRequest;
import com.hris.rag.entity.EvalSet;
import com.hris.rag.mapper.EvalSetMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * EvalSet 服务（CRUD）。
 */
@Service
public class EvalSetService {

    private final EvalSetMapper mapper;

    public EvalSetService(EvalSetMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询评测集详情，不存在则抛 NOT_FOUND。 */
    public EvalSetResponse get(Long id) {
        EvalSet e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "EvalSet 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询评测集列表。 */
    public PageResult<EvalSetResponse> list(long page, long size) {
        IPage<EvalSet> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<EvalSetResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建评测集。 */
    @Transactional(rollbackFor = Exception.class)
    public EvalSetResponse create(EvalSetCreateRequest req) {
        EvalSet e = new EvalSet();
        e.setName(req.name());
        e.setCode(req.code());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新评测集（乐观锁防并发覆盖）。 */
    @Transactional(rollbackFor = Exception.class)
    public EvalSetResponse update(Long id, EvalSetUpdateRequest req) {
        EvalSet e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "EvalSet 不存在");
        }
        e.setName(req.name());
        e.setCode(req.code());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除评测集（逻辑删除）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "EvalSet 不存在");
        }
    }

    private EvalSetResponse toResponse(EvalSet e) {
        return new EvalSetResponse(e.getId(), e.getName(), e.getCode(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

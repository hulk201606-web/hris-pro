package com.hris.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.rag.dto.SpaceDtos.SpaceCreateRequest;
import com.hris.rag.dto.SpaceDtos.SpaceResponse;
import com.hris.rag.dto.SpaceDtos.SpaceUpdateRequest;
import com.hris.rag.entity.Space;
import com.hris.rag.mapper.SpaceMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Space 服务（CRUD）。
 */
@Service
public class SpaceService {

    private final SpaceMapper mapper;

    public SpaceService(SpaceMapper mapper) {
        this.mapper = mapper;
    }

    public SpaceResponse get(Long id) {
        Space e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Space 不存在");
        }
        return toResponse(e);
    }

    public PageResult<SpaceResponse> list(long page, long size) {
        IPage<Space> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<SpaceResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public SpaceResponse create(SpaceCreateRequest req) {
        Space e = new Space();
        e.setName(req.name());
        e.setCode(req.code());
        e.setDescription(req.description());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public SpaceResponse update(Long id, SpaceUpdateRequest req) {
        Space e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Space 不存在");
        }
        e.setName(req.name());
        e.setCode(req.code());
        e.setDescription(req.description());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Space 不存在");
        }
    }

    private SpaceResponse toResponse(Space e) {
        return new SpaceResponse(e.getId(), e.getName(), e.getCode(), e.getDescription(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

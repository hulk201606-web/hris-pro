package com.hris.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.rag.dto.AclTagDtos.AclTagCreateRequest;
import com.hris.rag.dto.AclTagDtos.AclTagResponse;
import com.hris.rag.dto.AclTagDtos.AclTagUpdateRequest;
import com.hris.rag.entity.AclTag;
import com.hris.rag.mapper.AclTagMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AclTag 服务（CRUD）。
 */
@Service
public class AclTagService {

    private final AclTagMapper mapper;

    public AclTagService(AclTagMapper mapper) {
        this.mapper = mapper;
    }

    public AclTagResponse get(Long id) {
        AclTag e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "AclTag 不存在");
        }
        return toResponse(e);
    }

    public PageResult<AclTagResponse> list(long page, long size) {
        IPage<AclTag> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<AclTagResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public AclTagResponse create(AclTagCreateRequest req) {
        AclTag e = new AclTag();
        e.setName(req.name());
        e.setCode(req.code());
        e.setTagType(req.tagType());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public AclTagResponse update(Long id, AclTagUpdateRequest req) {
        AclTag e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "AclTag 不存在");
        }
        e.setName(req.name());
        e.setCode(req.code());
        e.setTagType(req.tagType());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "AclTag 不存在");
        }
    }

    private AclTagResponse toResponse(AclTag e) {
        return new AclTagResponse(e.getId(), e.getName(), e.getCode(), e.getTagType(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

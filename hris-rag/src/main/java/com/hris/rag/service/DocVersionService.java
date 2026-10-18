package com.hris.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.rag.dto.DocVersionDtos.DocVersionCreateRequest;
import com.hris.rag.dto.DocVersionDtos.DocVersionResponse;
import com.hris.rag.dto.DocVersionDtos.DocVersionUpdateRequest;
import com.hris.rag.entity.DocVersion;
import com.hris.rag.mapper.DocVersionMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DocVersion 服务（CRUD）。
 */
@Service
public class DocVersionService {

    private final DocVersionMapper mapper;

    public DocVersionService(DocVersionMapper mapper) {
        this.mapper = mapper;
    }

    public DocVersionResponse get(Long id) {
        DocVersion e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "DocVersion 不存在");
        }
        return toResponse(e);
    }

    public PageResult<DocVersionResponse> list(long page, long size) {
        IPage<DocVersion> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<DocVersionResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public DocVersionResponse create(DocVersionCreateRequest req) {
        DocVersion e = new DocVersion();
        e.setDocumentId(req.documentId());
        e.setStatus(req.status());
        e.setEffectiveFrom(req.effectiveFrom());
        e.setEffectiveTo(req.effectiveTo());
        e.setMilvusCollection(req.milvusCollection());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public DocVersionResponse update(Long id, DocVersionUpdateRequest req) {
        DocVersion e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "DocVersion 不存在");
        }
        e.setDocumentId(req.documentId());
        e.setStatus(req.status());
        e.setEffectiveFrom(req.effectiveFrom());
        e.setEffectiveTo(req.effectiveTo());
        e.setMilvusCollection(req.milvusCollection());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "DocVersion 不存在");
        }
    }

    private DocVersionResponse toResponse(DocVersion e) {
        return new DocVersionResponse(e.getId(), e.getDocumentId(), e.getStatus(), e.getEffectiveFrom(), e.getEffectiveTo(), e.getMilvusCollection(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

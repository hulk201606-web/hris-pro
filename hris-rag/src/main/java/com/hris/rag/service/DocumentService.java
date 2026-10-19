package com.hris.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.rag.dto.DocumentDtos.DocumentCreateRequest;
import com.hris.rag.dto.DocumentDtos.DocumentResponse;
import com.hris.rag.dto.DocumentDtos.DocumentUpdateRequest;
import com.hris.rag.entity.Document;
import com.hris.rag.mapper.DocumentMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Document 服务（CRUD）。
 */
@Service
public class DocumentService {

    private final DocumentMapper mapper;

    public DocumentService(DocumentMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询文档详情，不存在则抛 NOT_FOUND。 */
    public DocumentResponse get(Long id) {
        Document e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Document 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询文档列表。 */
    public PageResult<DocumentResponse> list(long page, long size) {
        IPage<Document> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<DocumentResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建文档。 */
    @Transactional(rollbackFor = Exception.class)
    public DocumentResponse create(DocumentCreateRequest req) {
        Document e = new Document();
        e.setSpaceId(req.spaceId());
        e.setTitle(req.title());
        e.setTitleHash(req.titleHash());
        e.setDocType(req.docType());
        e.setFileId(req.fileId());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新文档（乐观锁防并发覆盖）。 */
    @Transactional(rollbackFor = Exception.class)
    public DocumentResponse update(Long id, DocumentUpdateRequest req) {
        Document e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Document 不存在");
        }
        e.setSpaceId(req.spaceId());
        e.setTitle(req.title());
        e.setTitleHash(req.titleHash());
        e.setDocType(req.docType());
        e.setFileId(req.fileId());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除文档（逻辑删除）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Document 不存在");
        }
    }

    private DocumentResponse toResponse(Document e) {
        return new DocumentResponse(e.getId(), e.getSpaceId(), e.getTitle(), e.getTitleHash(), e.getDocType(), e.getFileId(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

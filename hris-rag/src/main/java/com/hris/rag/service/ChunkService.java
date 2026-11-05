package com.hris.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.rag.dto.ChunkDtos.ChunkCreateRequest;
import com.hris.rag.dto.ChunkDtos.ChunkResponse;
import com.hris.rag.dto.ChunkDtos.ChunkUpdateRequest;
import com.hris.rag.entity.Chunk;
import com.hris.rag.mapper.ChunkMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Chunk 服务（CRUD）。
 */
@Service
public class ChunkService {

    private final ChunkMapper mapper;
    private final SensitiveWordService sensitiveWordService;

    public ChunkService(ChunkMapper mapper, SensitiveWordService sensitiveWordService) {
        this.mapper = mapper;
        this.sensitiveWordService = sensitiveWordService;
    }

    /** 按主键查询文档切片详情，不存在则抛 NOT_FOUND。 */
    public ChunkResponse get(Long id) {
        Chunk e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Chunk 不存在");
        }
        return toResponse(e, List.of());
    }

    /** 分页查询文档切片列表。 */
    public PageResult<ChunkResponse> list(long page, long size) {
        IPage<Chunk> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<ChunkResponse> records = pg.getRecords().stream()
                .map(e -> toResponse(e, List.of())).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /**
     * 新建文档切片（KBD-F-017）：切片正文先过敏感词扫描，BLOCK 命中拒绝落库，
     * WARN 命中正常落库并在响应 warnings 透出。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChunkResponse create(ChunkCreateRequest req) {
        List<String> warnings = sensitiveWordService.scan(req.text());
        Chunk e = new Chunk();
        e.setDocVersionId(req.docVersionId());
        e.setSpaceId(req.spaceId());
        e.setSeq(req.seq());
        e.setHeadingPath(req.headingPath());
        e.setText(req.text());
        e.setTokenCnt(req.tokenCnt());
        e.setMilvusId(req.milvusId());
        e.setEsId(req.esId());
        e.setAclTags(req.aclTags());
        mapper.insert(e);
        return toResponse(e, warnings);
    }

    /** 更新文档切片（乐观锁防并发覆盖），正文同样过敏感词扫描。 */
    @Transactional(rollbackFor = Exception.class)
    public ChunkResponse update(Long id, ChunkUpdateRequest req) {
        Chunk e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Chunk 不存在");
        }
        List<String> warnings = sensitiveWordService.scan(req.text());
        e.setDocVersionId(req.docVersionId());
        e.setSpaceId(req.spaceId());
        e.setSeq(req.seq());
        e.setHeadingPath(req.headingPath());
        e.setText(req.text());
        e.setTokenCnt(req.tokenCnt());
        e.setMilvusId(req.milvusId());
        e.setEsId(req.esId());
        e.setAclTags(req.aclTags());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e, warnings);
    }

    /** 删除文档切片（逻辑删除）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Chunk 不存在");
        }
    }

    private ChunkResponse toResponse(Chunk e, List<String> warnings) {
        return new ChunkResponse(e.getId(), e.getDocVersionId(), e.getSpaceId(), e.getSeq(),
                e.getHeadingPath(), e.getText(), e.getTokenCnt(), e.getMilvusId(), e.getEsId(),
                e.getAclTags(), warnings, e.getCreatedAt(), e.getUpdatedAt());
    }
}

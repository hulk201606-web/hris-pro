package com.hris.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.rag.config.MilvusProperties;
import com.hris.rag.entity.Chunk;
import com.hris.rag.mapper.ChunkMapper;
import com.hris.rag.vector.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识切片索引重建（docs/05 §1 管线「定时/手动重建」入口）。
 *
 * <p>阶段 B：chunk.text 经 EmbeddingService 向量化（配置 Key 走 text-embedding-v4，
 * 未配置降级占位向量），写入 Milvus 并回填 milvus_id。</p>
 */
@Service
public class IndexRebuildService {

    private final ChunkMapper chunkMapper;
    private final VectorStore vectorStore;
    private final MilvusProperties props;
    private final EmbeddingService embeddingService;

    public IndexRebuildService(ChunkMapper chunkMapper, VectorStore vectorStore, MilvusProperties props,
                               EmbeddingService embeddingService) {
        this.chunkMapper = chunkMapper;
        this.vectorStore = vectorStore;
        this.props = props;
        this.embeddingService = embeddingService;
    }

    /**
     * 重建指定空间的切片索引。
     *
     * @param spaceId 知识空间 ID（集合名 kb_{tenantId} 由 tenantId 决定）
     * @return 已重建切片数
     */
    @Transactional(rollbackFor = Exception.class)
    public int rebuild(Long spaceId) {
        List<Chunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<Chunk>().eq(Chunk::getSpaceId, spaceId));
        if (chunks.isEmpty()) {
            return 0;
        }
        Long tenantId = chunks.get(0).getTenantId();
        String collection = "kb_" + tenantId;
        vectorStore.ensureCollection(collection, props.getDimension());

        int rebuilt = 0;
        for (Chunk chunk : chunks) {
            List<Float> vector = embeddingService.embed(chunk.getText(), chunk.getId());
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("space_id", chunk.getSpaceId());
            meta.put("doc_version_id", chunk.getDocVersionId());
            meta.put("seq", chunk.getSeq());
            if (chunk.getAclTags() != null && !chunk.getAclTags().isBlank()) {
                meta.put("acl_tags", chunk.getAclTags());
            }
            vectorStore.upsert(collection, chunk.getId(), vector, meta);
            if (chunk.getMilvusId() == null) {
                chunk.setMilvusId(String.valueOf(chunk.getId()));
                chunkMapper.updateById(chunk);
            }
            rebuilt++;
        }
        return rebuilt;
    }

    /** 校验向量库连通性（启动冒烟 / 排障）。 */
    public boolean ping() {
        try {
            vectorStore.ensureCollection("kb_ping_" + System.currentTimeMillis(), 2);
            return true;
        } catch (Exception e) {
            throw new BizException(ErrorCode.RAG_ERROR, "向量库不可达: " + e.getMessage());
        }
    }
}

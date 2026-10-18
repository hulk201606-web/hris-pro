package com.hris.rag.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.rag.config.MilvusProperties;
import com.hris.rag.entity.Chunk;
import com.hris.rag.mapper.ChunkMapper;
import com.hris.rag.vector.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IndexRebuildService 单元测试（kb_chunk → Milvus → milvus_id 回填链路）。
 *
 * <p>锁定行为：空空间直接返回 0（不动向量库）、集合名按租户隔离（kb_{tenantId}）、
 * 每个 chunk 经 EmbeddingService 向量化后 upsert、milvus_id 仅缺失时回填（幂等）、
 * 元数据携带 space_id/doc_version_id/seq/acl_tags（ACL 过滤依赖）、ping 不可达抛 RAG_ERROR。</p>
 */
@ExtendWith(MockitoExtension.class)
class IndexRebuildServiceTest {

    @Mock
    private ChunkMapper chunkMapper;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private EmbeddingService embeddingService;

    private static final int DIM = 1024;

    private IndexRebuildService service;

    @BeforeEach
    void setUp() {
        MilvusProperties props = new MilvusProperties();
        props.setDimension(DIM);
        service = new IndexRebuildService(chunkMapper, vectorStore, props, embeddingService);
    }

    private Chunk chunk(Long id, String milvusId, String aclTags) {
        Chunk c = new Chunk();
        c.setId(id);
        c.setTenantId(100001L);
        c.setSpaceId(900001L);
        c.setDocVersionId(800001L);
        c.setSeq(1);
        c.setText("切片文本-" + id);
        c.setAclTags(aclTags);
        c.setMilvusId(milvusId);
        return c;
    }

    @Test
    @DisplayName("空空间 → 返回 0 且不触碰向量库")
    void emptySpaceReturnsZero() {
        when(chunkMapper.selectList(any())).thenReturn(List.of());

        assertEquals(0, service.rebuild(900001L));
        verify(vectorStore, never()).ensureCollection(any(), eq(DIM));
    }

    @Test
    @DisplayName("happy path：按租户建集合、逐 chunk 向量化 upsert、缺失才回填 milvus_id")
    void rebuildUpsertsAndBackfills() {
        Chunk fresh = chunk(900001L, null, "[\"HR\"]");
        Chunk done = chunk(900002L, "existing-milvus-id", null);
        when(chunkMapper.selectList(any())).thenReturn(List.of(fresh, done));
        when(embeddingService.embed(any(), anyLong())).thenReturn(List.of(0.1f, 0.2f));
        when(chunkMapper.updateById(any(Chunk.class))).thenReturn(1);

        int rebuilt = service.rebuild(900001L);

        assertEquals(2, rebuilt);
        verify(vectorStore).ensureCollection("kb_100001", DIM);
        verify(embeddingService, times(2)).embed(any(), anyLong());
        verify(vectorStore, times(2)).upsert(eq("kb_100001"), anyLong(), anyList(), anyMap());
        // 仅缺失者回填；已有 milvus_id 不重复写库
        verify(chunkMapper, times(1)).updateById(fresh);
        verify(chunkMapper, never()).updateById(done);
        assertEquals("900001", fresh.getMilvusId());
    }

    @Test
    @DisplayName("upsert 元数据携带 space_id/doc_version_id/seq/acl_tags（ACL 过滤依赖）")
    void upsertMetaCarriesAclFields() {
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk(900001L, null, "[\"HR\",\"FIN\"]")));
        when(embeddingService.embed(any(), anyLong())).thenReturn(List.of(0.5f));
        when(chunkMapper.updateById(any(Chunk.class))).thenReturn(1);

        service.rebuild(900001L);

        ArgumentCaptor<Map<String, Object>> metaCaptor = ArgumentCaptor.forClass(Map.class);
        verify(vectorStore).upsert(eq("kb_100001"), eq(900001L), anyList(), metaCaptor.capture());
        Map<String, Object> meta = metaCaptor.getValue();
        assertEquals(900001L, meta.get("space_id"));
        assertEquals(800001L, meta.get("doc_version_id"));
        assertEquals(1, meta.get("seq"));
        assertEquals("[\"HR\",\"FIN\"]", meta.get("acl_tags"));
    }

    @Test
    @DisplayName("ping：向量库不可达 → RAG_ERROR")
    void pingThrowsWhenStoreUnavailable() {
        // ensureCollection 返回 void，桩异常须用 doThrow().when() 形式
        doThrow(new RuntimeException("connection refused"))
                .when(vectorStore).ensureCollection(anyString(), eq(2));

        BizException e = assertThrows(BizException.class, () -> service.ping());
        assertEquals(ErrorCode.RAG_ERROR.getCode(), e.getCode());
    }
}

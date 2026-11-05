package com.hris.rag.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Chunk DTO（由 DDL 自动生成）。
 */
public final class ChunkDtos {

    private ChunkDtos() {
    }

    /** 创建请求。 */
    public record ChunkCreateRequest(@NotNull(message = "docVersionId 不能为空") Long docVersionId, @NotNull(message = "spaceId 不能为空") Long spaceId, @NotNull(message = "seq 不能为空") Integer seq, String headingPath, String text, @NotNull(message = "tokenCnt 不能为空") Integer tokenCnt, String milvusId, String esId, String aclTags) {
    }

    /** 更新请求。 */
    public record ChunkUpdateRequest(Long docVersionId, Long spaceId, Integer seq, String headingPath, String text, Integer tokenCnt, String milvusId, String esId, String aclTags) {
    }

    /** 响应；warnings 为敏感词 WARN 级命中词（KBD-F-017）。 */
    public record ChunkResponse(Long id, Long docVersionId, Long spaceId, Integer seq, String headingPath, String text, Integer tokenCnt, String milvusId, String esId, String aclTags, List<String> warnings, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

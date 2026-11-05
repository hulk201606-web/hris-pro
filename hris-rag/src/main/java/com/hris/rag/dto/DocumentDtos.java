package com.hris.rag.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Document DTO（由 DDL 自动生成）。
 */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    /** 创建请求。 */
    public record DocumentCreateRequest(@NotNull(message = "spaceId 不能为空") Long spaceId, @NotNull(message = "title 不能为空") String title, @NotNull(message = "titleHash 不能为空") String titleHash, @NotNull(message = "docType 不能为空") String docType, Long fileId) {
    }

    /** 更新请求。 */
    public record DocumentUpdateRequest(Long spaceId, String title, String titleHash, String docType, Long fileId) {
    }

    /** 响应；warnings 为敏感词 WARN 级命中词（KBD-F-017），BLOCK 命中直接拒绝不返回。 */
    public record DocumentResponse(Long id, Long spaceId, String title, String titleHash, String docType, Long fileId, List<String> warnings, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

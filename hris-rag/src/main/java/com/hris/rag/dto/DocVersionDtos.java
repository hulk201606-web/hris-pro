package com.hris.rag.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DocVersion DTO（由 DDL 自动生成）。
 */
public final class DocVersionDtos {

    private DocVersionDtos() {
    }

    /** 创建请求。 */
    public record DocVersionCreateRequest(@NotNull(message = "documentId 不能为空") Long documentId, @NotNull(message = "status 不能为空") String status, LocalDate effectiveFrom, LocalDate effectiveTo, String milvusCollection) {
    }

    /** 更新请求。 */
    public record DocVersionUpdateRequest(Long documentId, String status, LocalDate effectiveFrom, LocalDate effectiveTo, String milvusCollection) {
    }

    /** 响应。 */
    public record DocVersionResponse(Long id, Long documentId, String status, LocalDate effectiveFrom, LocalDate effectiveTo, String milvusCollection, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

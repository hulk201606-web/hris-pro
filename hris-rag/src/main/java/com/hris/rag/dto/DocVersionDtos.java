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

    /**
     * 创建请求（服务端强制语义：新版本恒以 DRAFT 起步，status 不可指定；
     * 状态只走 submit 发布提交 / 审批回调 / 归档动作）。
     */
    public record DocVersionCreateRequest(@NotNull(message = "documentId 不能为空") Long documentId, LocalDate effectiveFrom, LocalDate effectiveTo, String milvusCollection) {
    }

    /**
     * 更新请求（旁路清理：status 不可经更新直填——状态只走 submit/回调/归档动作）。
     */
    public record DocVersionUpdateRequest(Long documentId, LocalDate effectiveFrom, LocalDate effectiveTo, String milvusCollection) {
    }

    /** 响应。 */
    public record DocVersionResponse(Long id, Long documentId, String status, LocalDate effectiveFrom, LocalDate effectiveTo, String milvusCollection, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

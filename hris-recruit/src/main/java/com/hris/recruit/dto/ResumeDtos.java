package com.hris.recruit.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Resume DTO（由 DDL 自动生成）。
 */
public final class ResumeDtos {

    private ResumeDtos() {
    }

    /** 创建请求。 */
    public record ResumeCreateRequest(@NotNull(message = "candidateId 不能为空") Long candidateId, Long fileId, String parseResult) {
    }

    /** 更新请求。 */
    public record ResumeUpdateRequest(Long candidateId, Long fileId, String parseResult) {
    }

    /** 响应。 */
    public record ResumeResponse(Long id, Long candidateId, Long fileId, String parseResult, LocalDate retentionUntil, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** 过期简历清理响应（RCS-F-022）：cleaned = 本次逻辑删除条数。 */
    public record ResumeCleanupResponse(int cleaned) {
    }
}

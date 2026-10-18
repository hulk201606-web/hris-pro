package com.hris.recruit.dto;

import jakarta.validation.constraints.NotNull;
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
    public record ResumeResponse(Long id, Long candidateId, Long fileId, String parseResult, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

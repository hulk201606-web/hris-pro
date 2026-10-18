package com.hris.recruit.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * JobOpening DTO（由 DDL 自动生成）。
 */
public final class JobOpeningDtos {

    private JobOpeningDtos() {
    }

    /** 创建请求。 */
    public record JobOpeningCreateRequest(Long requisitionId, @NotNull(message = "code 不能为空") String code, @NotNull(message = "title 不能为空") String title, String description, @NotNull(message = "status 不能为空") String status, LocalDateTime publishAt) {
    }

    /** 更新请求。 */
    public record JobOpeningUpdateRequest(Long requisitionId, String code, String title, String description, String status, LocalDateTime publishAt) {
    }

    /** 响应。 */
    public record JobOpeningResponse(Long id, Long requisitionId, String code, String title, String description, String status, LocalDateTime publishAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

package com.hris.recruit.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Candidate DTO（由 DDL 自动生成）。
 */
public final class CandidateDtos {

    private CandidateDtos() {
    }

    /** 创建请求。 */
    public record CandidateCreateRequest(@NotNull(message = "name 不能为空") String name, String mobileCipher, String mobileMask, String email, @NotNull(message = "status 不能为空") String status) {
    }

    /** 更新请求。 */
    public record CandidateUpdateRequest(String name, String mobileCipher, String mobileMask, String email, String status) {
    }

    /** 响应。 */
    public record CandidateResponse(Long id, String name, String mobileCipher, String mobileMask, String email, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

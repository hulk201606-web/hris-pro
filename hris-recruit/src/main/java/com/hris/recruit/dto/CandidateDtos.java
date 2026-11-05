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

    /**
     * 响应。mobileMask 由 mobileCipher（实为明文，见实体注释）在输出侧脱敏生成，
     * 手机号原文不出候选人域（docs/19 §8.7-3；入职转化走实体字段不经此 DTO）。
     */
    public record CandidateResponse(Long id, String name, String mobileMask, String email, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

package com.hris.recruit.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * TalentPoolTag DTO（由 DDL 自动生成）。
 */
public final class TalentPoolTagDtos {

    private TalentPoolTagDtos() {
    }

    /** 创建请求。 */
    public record TalentPoolTagCreateRequest(@NotNull(message = "candidateId 不能为空") Long candidateId, @NotNull(message = "tag 不能为空") String tag) {
    }

    /** 更新请求。 */
    public record TalentPoolTagUpdateRequest(Long candidateId, String tag) {
    }

    /** 响应。 */
    public record TalentPoolTagResponse(Long id, Long candidateId, String tag, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

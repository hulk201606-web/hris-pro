package com.hris.rag.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * AclTag DTO（由 DDL 自动生成）。
 */
public final class AclTagDtos {

    private AclTagDtos() {
    }

    /** 创建请求。 */
    public record AclTagCreateRequest(@NotNull(message = "name 不能为空") String name, @NotNull(message = "code 不能为空") String code, @NotNull(message = "tagType 不能为空") String tagType) {
    }

    /** 更新请求。 */
    public record AclTagUpdateRequest(String name, String code, String tagType) {
    }

    /** 响应。 */
    public record AclTagResponse(Long id, String name, String code, String tagType, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

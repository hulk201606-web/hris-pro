package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 薪资组相关 DTO（域：薪酬，分层：DTO/接口契约）。薪资组创建/更新/查询的请求与响应结构。
 */
public final class PayGroupDtos {

    private PayGroupDtos() {
    }

    /** 创建请求。 */
    public record PayGroupCreateRequest(@NotNull(message = "name 不能为空") String name, @NotNull(message = "code 不能为空") String code, @NotNull(message = "payCycle 不能为空") String payCycle) {
    }

    /** 更新请求。 */
    public record PayGroupUpdateRequest(String name, String code, String payCycle) {
    }

    /** 响应。 */
    public record PayGroupResponse(Long id, String name, String code, String payCycle, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

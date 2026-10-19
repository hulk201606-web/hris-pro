package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 薪资项目相关 DTO（域：薪酬，分层：DTO/接口契约）。薪资项目（工资条项）创建/更新/查询的请求与响应结构。
 */
public final class PayItemDtos {

    private PayItemDtos() {
    }

    /** 创建请求。 */
    public record PayItemCreateRequest(@NotNull(message = "name 不能为空") String name, @NotNull(message = "code 不能为空") String code, String formula, @NotNull(message = "status 不能为空") Integer status) {
    }

    /** 更新请求。 */
    public record PayItemUpdateRequest(String name, String code, String formula, Integer status) {
    }

    /** 响应。 */
    public record PayItemResponse(Long id, String name, String code, String formula, Integer status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

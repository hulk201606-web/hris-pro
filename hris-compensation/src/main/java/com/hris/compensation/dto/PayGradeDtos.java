package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 薪级相关 DTO（域：薪酬，分层：DTO/接口契约）。薪级创建/更新/查询的请求与响应结构。
 */
public final class PayGradeDtos {

    private PayGradeDtos() {
    }

    /** 创建请求。 */
    public record PayGradeCreateRequest(@NotNull(message = "name 不能为空") String name, @NotNull(message = "code 不能为空") String code, BigDecimal minSalary, BigDecimal maxSalary) {
    }

    /** 更新请求。 */
    public record PayGradeUpdateRequest(String name, String code, BigDecimal minSalary, BigDecimal maxSalary) {
    }

    /** 响应。 */
    public record PayGradeResponse(Long id, String name, String code, BigDecimal minSalary, BigDecimal maxSalary, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

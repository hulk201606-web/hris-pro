package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PayGrade DTO（由 DDL 自动生成）。
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

package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SocialFundProfile DTO（由 DDL 自动生成）。
 */
public final class SocialFundProfileDtos {

    private SocialFundProfileDtos() {
    }

    /** 创建请求。 */
    public record SocialFundProfileCreateRequest(@NotNull(message = "empId 不能为空") Long empId, @NotNull(message = "effectiveFrom 不能为空") LocalDate effectiveFrom, LocalDate effectiveTo, @NotNull(message = "fundType 不能为空") String fundType, @NotNull(message = "baseAmount 不能为空") BigDecimal baseAmount) {
    }

    /** 更新请求。 */
    public record SocialFundProfileUpdateRequest(Long empId, LocalDate effectiveFrom, LocalDate effectiveTo, String fundType, BigDecimal baseAmount) {
    }

    /** 响应。 */
    public record SocialFundProfileResponse(Long id, Long empId, LocalDate effectiveFrom, LocalDate effectiveTo, String fundType, BigDecimal baseAmount, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

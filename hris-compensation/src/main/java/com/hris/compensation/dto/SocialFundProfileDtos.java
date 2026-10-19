package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 社保公积金档案相关 DTO（域：薪酬，分层：DTO/接口契约）。社保/公积金缴纳基数配置的创建/更新/查询结构。
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

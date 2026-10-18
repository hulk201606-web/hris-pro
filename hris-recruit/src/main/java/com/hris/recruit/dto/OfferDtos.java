package com.hris.recruit.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Offer DTO（由 DDL 自动生成）。
 */
public final class OfferDtos {

    private OfferDtos() {
    }

    /** 创建请求。 */
    public record OfferCreateRequest(@NotNull(message = "applicationId 不能为空") Long applicationId, @NotNull(message = "offerNo 不能为空") String offerNo, Integer salaryBandCheck, String salaryAmountCipher, String salaryAmountMask, @NotNull(message = "status 不能为空") String status, String procInstId) {
    }

    /** 更新请求。 */
    public record OfferUpdateRequest(Long applicationId, String offerNo, Integer salaryBandCheck, String salaryAmountCipher, String salaryAmountMask, String status, String procInstId) {
    }

    /** 响应。 */
    public record OfferResponse(Long id, Long applicationId, String offerNo, Integer salaryBandCheck, String salaryAmountCipher, String salaryAmountMask, String status, String procInstId, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

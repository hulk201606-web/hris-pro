package com.hris.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Offer DTO（由 DDL 自动生成 + RCS-F-018 发送/回执扩展）。
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

    /** 响应；receiptType/receiptNote/receiptAt 为候选人回执（RCS-F-018）。 */
    public record OfferResponse(Long id, Long applicationId, String offerNo, Integer salaryBandCheck, String salaryAmountCipher, String salaryAmountMask, String status, String procInstId, String receiptType, String receiptNote, LocalDateTime receiptAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** 发送请求：channel 仅允许 EMAIL/WECOM/DINGTALK，recipient 缺省时由 PLT 按模板收件人投递。 */
    public record OfferSendRequest(@NotBlank(message = "channel 不能为空") String channel, String recipient) {
    }

    /** 发送结果：status=SENT/FAILED（FAILED 不伪造 SENT，error 透出原因）。 */
    public record OfferSendResponse(Long offerId, Long logId, String status, String error, String offerStatus) {
    }

    /** 回执请求：type 仅 ACCEPTED/DECLINED，note 可选；重复回执覆盖并更新回执时间。 */
    public record OfferReceiptRequest(@NotBlank(message = "type 不能为空") String type, String note) {
    }
}

package com.hris.rag.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * GovernTicket DTO（由 DDL 自动生成）。
 */
public final class GovernTicketDtos {

    private GovernTicketDtos() {
    }

    /** 创建请求。 */
    public record GovernTicketCreateRequest(Long docVersionId, @NotNull(message = "source 不能为空") String source, @NotNull(message = "status 不能为空") String status, String description) {
    }

    /** 更新请求。 */
    public record GovernTicketUpdateRequest(Long docVersionId, String source, String status, String description) {
    }

    /** 响应。 */
    public record GovernTicketResponse(Long id, Long docVersionId, String source, String status, String description, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CreditLedger DTO（由 DDL 自动生成）。
 */
public final class CreditLedgerDtos {

    private CreditLedgerDtos() {
    }

    /** 创建请求。 */
    public record CreditLedgerCreateRequest(@NotNull(message = "empId 不能为空") Long empId, Long courseId, @NotNull(message = "credits 不能为空") BigDecimal credits) {
    }

    /** 更新请求。 */
    public record CreditLedgerUpdateRequest(Long empId, Long courseId, BigDecimal credits) {
    }

    /** 响应。 */
    public record CreditLedgerResponse(Long id, Long empId, Long courseId, BigDecimal credits, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

package com.hris.performance.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学分台账相关 DTO（域：绩效/培训，分层：DTO/接口契约）。学分累计/查询的数据结构。
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

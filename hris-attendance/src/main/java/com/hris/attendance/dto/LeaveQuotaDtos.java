package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 假期额度相关 DTO（域：考勤，分层：DTO/接口契约）。额度发放/结转/查询的请求与响应结构。
 */
public final class LeaveQuotaDtos {

    private LeaveQuotaDtos() {
    }

    /** 额度发放请求：amount 为天，正增负减。 */
    public record QuotaGrantRequest(
            @NotNull(message = "员工 ID 不能为空") Long empId,
            @NotNull(message = "假种 ID 不能为空") Long leaveTypeId,
            @NotBlank(message = "周期不能为空") String period,
            @NotNull(message = "额度不能为空") BigDecimal amount) {
    }

    /**
     * 额度结转请求：把 from_period 的可用额度转到 to_period。
     * 受假种 rules.carryOverMax 约束；rules 无该键时按 MAX_CARRY_OVER_DAYS 兜底。
     */
    public record QuotaTransferRequest(
            @NotNull(message = "员工 ID 不能为空") Long empId,
            @NotNull(message = "假种 ID 不能为空") Long leaveTypeId,
            @NotBlank(message = "源周期不能为空") String fromPeriod,
            @NotBlank(message = "目标周期不能为空") String toPeriod,
            @NotNull(message = "结转额度不能为空") BigDecimal amount) {
    }

    /** 额度响应。availableQuota = totalQuota - usedQuota（派生，不落库）。 */
    public record LeaveQuotaResponse(Long id, Long empId, Long leaveTypeId, String leaveTypeName,
                                     String period, BigDecimal totalQuota, BigDecimal usedQuota,
                                     BigDecimal availableQuota) {
    }

    /** 额度流水响应。 */
    public record LeaveLedgerResponse(Long id, Long empId, Long leaveTypeId, String leaveTypeName,
                                      BigDecimal changeAmount, String bizRef,
                                      LocalDateTime createdAt) {
    }

    /** 员工余额视图。 */
    public record QuotaBalanceResponse(Long empId, List<LeaveQuotaResponse> quotas) {
    }
}

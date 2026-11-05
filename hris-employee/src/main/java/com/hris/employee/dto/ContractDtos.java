package com.hris.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 合同台账 DTO（EMP-F-014/015）。
 */
public final class ContractDtos {

    private ContractDtos() {
    }

    /** 合同创建请求。contractNo 为空则自动生成；legalEntityId 可选（多法人签订方维度）。 */
    public record ContractCreateRequest(
            @NotNull(message = "empId 不能为空") Long empId,
            @NotBlank(message = "type 不能为空") String type,
            @NotNull(message = "startDate 不能为空") LocalDate startDate,
            LocalDate endDate,
            LocalDate probationEnd,
            Long fileId,
            String contractNo,
            Long legalEntityId) {
    }

    /** 续签请求。 */
    public record ContractRenewRequest(
            @NotNull(message = "newEndDate 不能为空") LocalDate newEndDate) {
    }

    /** 合同详情响应。 */
    public record ContractResponse(
            Long id, Long empId, String contractNo, String type,
            LocalDate startDate, LocalDate endDate, LocalDate probationEnd,
            Long legalEntityId, Integer renewCnt, Long fileId, Integer status) {
    }

    /** 到期预警项（60/30/7 天多级）。 */
    public record ContractExpiringItem(
            Long id, Long empId, String empNo, String name, String contractNo,
            LocalDate endDate, long daysToExpire, String level) {
    }
}

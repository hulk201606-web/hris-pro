package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * PayrollRun DTO（由 DDL 自动生成）。
 */
public final class PayrollRunDtos {

    private PayrollRunDtos() {
    }

    /** 创建请求。 */
    public record PayrollRunCreateRequest(@NotNull(message = "periodCode 不能为空") String periodCode, @NotNull(message = "payGroupId 不能为空") Long payGroupId, @NotNull(message = "status 不能为空") String status, Long calcBy, Long reviewBy) {
    }

    /** 更新请求。 */
    public record PayrollRunUpdateRequest(String periodCode, Long payGroupId, String status, Long calcBy, Long reviewBy) {
    }

    /** 响应。 */
    public record PayrollRunResponse(Long id, String periodCode, Long payGroupId, String status, Long calcBy, Long reviewBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

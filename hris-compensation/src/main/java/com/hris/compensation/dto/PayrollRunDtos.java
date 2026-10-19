package com.hris.compensation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 算薪批次相关 DTO（域：薪酬，分层：DTO/接口契约）。算薪批次创建/更新/响应结构，含状态机字段。
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

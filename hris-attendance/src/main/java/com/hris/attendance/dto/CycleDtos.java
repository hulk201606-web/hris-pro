package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 绩效周期相关 DTO（域：绩效，分层：DTO/接口契约）。周期创建/更新/响应结构，含状态机字段。
 */
public final class CycleDtos {

    private CycleDtos() {
    }

    /**
     * 周期创建/更新请求。period_code 形如 2026-01；
     * start_date / end_date 支持非自然月切分（如上月 21 日至本月 20 日）。
     */
    public record CycleRequest(
            @NotBlank(message = "周期编码不能为空") String periodCode,
            @NotNull(message = "开始日期不能为空") LocalDate startDate,
            @NotNull(message = "结束日期不能为空") LocalDate endDate) {
    }

    /** 周期响应。 */
    public record CycleResponse(Long id, String periodCode, LocalDate startDate, LocalDate endDate,
                                String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

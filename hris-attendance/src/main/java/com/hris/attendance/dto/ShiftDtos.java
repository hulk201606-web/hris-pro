package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 班次 DTO（ATT-F-002 固定/弹性/跨天班次）。
 */
public final class ShiftDtos {

    private ShiftDtos() {
    }

    /**
     * 班次创建/更新请求。
     * flexible=1 表示弹性班次（只校验总时长，不卡上下班时刻）。
     * 跨天班次以 end_time 小于或等于 start_time 表达。
     */
    public record ShiftRequest(
            @NotBlank(message = "班次名称不能为空") String name,
            String code,
            @NotNull(message = "上班时间不能为空") LocalTime startTime,
            @NotNull(message = "下班时间不能为空") LocalTime endTime,
            Integer flexible) {
    }

    /** 班次响应。crossDay 由 end_time ≤ start_time 派生，便于前端展示。 */
    public record ShiftResponse(Long id, String name, String code, LocalTime startTime,
                                LocalTime endTime, Integer flexible, boolean crossDay,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 班次定义相关 DTO（域：考勤，分层：DTO/接口契约）。班次创建/更新/查询的请求与响应结构。
 */
public final class ShiftDtos {

    private ShiftDtos() {
    }

    /**
     * 班次创建/更新请求。
     * flexible=1 表示弹性班次（只校验总时长，不卡上下班时刻）。
     * crossDay=1 显式声明跨天班次（下班时间为次日，B-5）；留空时按 end_time<=start_time 派生。
     */
    public record ShiftRequest(
            @NotBlank(message = "班次名称不能为空") String name,
            String code,
            @NotNull(message = "上班时间不能为空") LocalTime startTime,
            @NotNull(message = "下班时间不能为空") LocalTime endTime,
            Integer flexible,
            Integer crossDay) {
    }

    /** 班次响应。crossDay 取持久化标志，历史数据由 end_time<=start_time 派生兜底。 */
    public record ShiftResponse(Long id, String name, String code, LocalTime startTime,
                                LocalTime endTime, Integer flexible, boolean crossDay,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

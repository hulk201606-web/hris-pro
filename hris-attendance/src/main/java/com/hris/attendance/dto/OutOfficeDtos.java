package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 外出/居家办公申请 DTO（域：考勤，分层：DTO/接口契约，ATT-F-018）。
 */
public final class OutOfficeDtos {

    private OutOfficeDtos() {
    }

    /**
     * 自助创建请求。empId 不由前端传入——取网关透传的当前用户 ID（self 语义，
     * 照 leave-quotas/me 的 userId→empId 解析范式）。
     */
    public record OutOfficeCreateRequest(
            @NotBlank(message = "类型不能为空（OUT=外出 / WFH=居家办公）") String type,
            @NotNull(message = "开始时间不能为空") LocalDateTime startTime,
            @NotNull(message = "结束时间不能为空") LocalDateTime endTime,
            String reason) {
    }

    /** 响应。 */
    public record OutOfficeResponse(Long id, Long empId, String type,
                                    LocalDateTime startTime, LocalDateTime endTime,
                                    String reason, String status, Long approverId,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

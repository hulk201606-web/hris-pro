package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 补卡申请相关 DTO（域：考勤，分层：DTO/接口契约）。补卡申请创建/查询/审批的请求与响应结构。
 */
public final class FixDtos {

    private FixDtos() {
    }

    /** 补卡申请请求。type 取 IN/OUT；同一 work_date 重复提交由月度上限与业务单号共同约束。 */
    public record FixCreateRequest(
            @NotNull(message = "员工 ID 不能为空") Long empId,
            @NotNull(message = "补卡日期不能为空") LocalDate workDate,
            @NotNull(message = "补卡时间不能为空") LocalDateTime fixTime,
            @NotBlank(message = "补卡类型不能为空") String type,
            String reason) {
    }

    /** 审批动作请求。 */
    public record FixActionRequest(String remark) {
    }

    /** 补卡单响应。 */
    public record FixResponse(Long id, Long empId, String bizKey, LocalDate workDate,
                              LocalDateTime fixTime, String type, String reason, String status,
                              String procInstId, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

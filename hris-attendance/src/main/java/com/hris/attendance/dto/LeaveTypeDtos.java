package com.hris.attendance.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 假种 DTO（ATT-F-011 假期类型配置）。
 */
public final class LeaveTypeDtos {

    private LeaveTypeDtos() {
    }

    /**
     * 假种创建/更新请求。
     * rules 为 JSON 字符串，建议键：paid(是否带薪)、needProof(是否需证明)、
     * minUnit(0.5h/0.5d)、seniorityTiers(司龄阶梯)、validMonths(有效期)、carryOverMax(结转上限)。
     */
    public record LeaveTypeRequest(
            @NotBlank(message = "假种名称不能为空") String name,
            String code,
            String rules) {
    }

    /** 假种响应。 */
    public record LeaveTypeResponse(Long id, String name, String code, String rules,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

package com.hris.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 编制设定请求（PUT /org/headcounts，按 dept+position+period 唯一约束 upsert）。
 */
public record HeadcountRequest(
        @NotNull(message = "部门 ID 不能为空") Long deptId,
        @NotNull(message = "岗位 ID 不能为空") Long positionId,
        @NotBlank(message = "编制周期不能为空") String period,
        @NotNull(message = "编制数不能为空") Integer quota) {
}

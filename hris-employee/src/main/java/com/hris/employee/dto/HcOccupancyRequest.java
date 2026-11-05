package com.hris.employee.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 编制占用回写请求（§10.5-B8，emp → org 内部契约；与 org 侧 OccupancyRequest 对齐）。
 * delta 仅 +1/-1；period 为空由 org 按当前季度定位。
 */
public record HcOccupancyRequest(
        @NotNull Long empId,
        @NotNull Long deptId,
        @NotNull Long positionId,
        String period,
        @NotNull Integer delta,
        @NotNull String transNo) {
}

package com.hris.org.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 编制占用回写请求（§10.5-B8，emp 落账经内部端点回写）。
 *
 * <p>delta 仅允许 +1/-1；transNo 为幂等键（同事务号不重复计）。
 * period 为空时按当前季度定位。</p>
 */
public record OccupancyRequest(
        @NotNull(message = "员工 ID 不能为空") Long empId,
        @NotNull(message = "部门 ID 不能为空") Long deptId,
        @NotNull(message = "岗位 ID 不能为空") Long positionId,
        String period,
        @NotNull(message = "delta 不能为空") Integer delta,
        @NotNull(message = "transNo 不能为空") String transNo) {
}

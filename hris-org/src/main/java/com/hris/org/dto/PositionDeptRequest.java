package com.hris.org.dto;

import java.util.List;

/**
 * 岗位×部门挂靠请求（POST /org/positions/{id}/depts，整体替换语义）。
 * deptIds 为空列表/缺省 = 清空挂靠。
 */
public record PositionDeptRequest(List<Long> deptIds) {
}

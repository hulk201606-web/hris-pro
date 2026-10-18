package com.hris.employee.dto;

/**
 * 编制校验结果（内部接口 /internal/org/hc/check 的本地契约副本，字段与 hris-org 对齐）。
 */
public record HcCheckResponse(boolean allowed, Integer quota, Integer occupied, Integer shortfall) {
}

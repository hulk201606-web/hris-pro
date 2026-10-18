package com.hris.org.dto;

/**
 * 编制校验结果（内部接口 /internal/org/hc/check）。
 */
public record HcCheckResponse(boolean allowed, Integer quota, Integer occupied, Integer shortfall) {
}

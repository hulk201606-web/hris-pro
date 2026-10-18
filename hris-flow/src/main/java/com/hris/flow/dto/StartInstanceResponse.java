package com.hris.flow.dto;

/**
 * 发起流程响应。
 */
public record StartInstanceResponse(
        String instanceId,
        String bizType,
        String bizKey,
        String status) {
}

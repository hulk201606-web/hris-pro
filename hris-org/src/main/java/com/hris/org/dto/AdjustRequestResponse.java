package com.hris.org.dto;

/**
 * 组织调整申请落单结果。flowInstanceId 在流程服务就绪后回填。
 */
public record AdjustRequestResponse(String bizKey, String status, String flowInstanceId) {
}

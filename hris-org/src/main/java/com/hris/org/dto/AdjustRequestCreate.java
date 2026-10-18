package com.hris.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 组织调整申请（发起 ORG_ADJUST 流程，MVP 落单 + 预留 flow 调用）。
 */
public record AdjustRequestCreate(
        @NotBlank(message = "调整动作不能为空") String action,
        Long deptId,
        @NotNull(message = "调整内容不能为空") DeptRequest payload,
        String reason) {
}

package com.hris.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 内部入职建号 / 离职禁用 DTO。
 */
public final class AccountDtos {

    private AccountDtos() {
    }

    /** 入职建号请求（内部 Feign，由 EMP 域调用）。 */
    public record AccountCreateRequest(
            @NotNull(message = "tenantId 不能为空") Long tenantId,
            @NotNull(message = "employeeId 不能为空") Long employeeId,
            @NotBlank(message = "username 不能为空") String username,
            @NotBlank(message = "password 不能为空") String password,
            List<String> roleCodes) {
    }

    /** 建号结果。 */
    public record AccountCreateResponse(Long userId, Long tenantId, Long employeeId) {
    }
}

package com.hris.employee.dto;

/**
 * 账号联动 DTO（内部 Feign 调用 hris-auth 的本地契约副本，字段与 auth 域 AccountDtos 对齐）。
 */
public final class AccountDtos {

    private AccountDtos() {
    }

    /** 入职建号请求。 */
    public record AccountCreateRequest(Long tenantId, Long employeeId, String username,
                                       String password, java.util.List<String> roleCodes) {
    }

    /** 建号结果。 */
    public record AccountCreateResponse(Long userId, Long tenantId, Long employeeId) {
    }
}

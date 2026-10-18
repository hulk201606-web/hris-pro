package com.hris.auth.dto;

import java.util.List;

/**
 * 当前用户+权限摘要+租户品牌（GET /auth/me）。
 */
public record MeResponse(
        UserInfo user,
        List<RoleInfo> roles,
        List<String> perms,
        List<DataScopeInfo> dataScopes,
        TenantBrand tenant) {

    public record UserInfo(Long userId, Long employeeId, String username, Integer status) {
    }

    public record RoleInfo(String code, String name) {
    }

    public record DataScopeInfo(String roleCode, String scopeType, String deptIds) {
    }

    public record TenantBrand(Long tenantId, String code, String name, String plan,
                              String timezone, String domain) {
    }
}

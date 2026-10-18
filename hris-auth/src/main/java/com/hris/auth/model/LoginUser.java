package com.hris.auth.model;

import java.util.List;

/**
 * 已认证用户上下文（JWT claims 派生或登录后构建）。
 */
public record LoginUser(
        Long userId,
        Long tenantId,
        Long employeeId,
        String username,
        Integer status,
        List<String> roles,
        List<String> perms) {
}

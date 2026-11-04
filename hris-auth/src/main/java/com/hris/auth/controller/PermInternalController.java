package com.hris.auth.controller;

import com.hris.auth.service.UserAuthService;
import com.hris.common.api.R;
import com.hris.common.trace.TraceContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限内部端点（`/internal/` 前缀，被网关 InternalBanWebFilter 封禁公网访问，仅服务间直连）。
 * 供网关验签后经 {@code lb://hris-auth} 实时查询当前用户权限点。
 *
 * <p>内部调用无 Authorization 头，JwtAuthenticationFilter 不会还原租户；而 auth_user_role /
 * auth_role_perm 均受 MyBatis-Plus 租户插件拦截（追加 tenant_id 条件）。故本端点必须显式
 * 还原 tenantId 到 TraceContext，否则查询恒为空（tenant_id=0 匹配不到任何数据）。</p>
 */
@RestController
@RequestMapping("/internal/auth")
public class PermInternalController {

    private final UserAuthService userAuthService;

    public PermInternalController(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    /**
     * 按 userId 查权限点编码集合（聚合角色下所有 auth_role_perm.perm_code，去重）。
     * tenantId 由网关透传，用于还原租户上下文，避免租户插件注入 tenant_id=0 导致查询为空。
     */
    @GetMapping("/users/{userId}/perms")
    public R<List<String>> perms(@PathVariable Long userId, @RequestParam Long tenantId) {
        TraceContext.setTenantId(tenantId);
        try {
            return R.ok(userAuthService.permsOf(userId));
        } finally {
            TraceContext.setTenantId(null);
        }
    }
}

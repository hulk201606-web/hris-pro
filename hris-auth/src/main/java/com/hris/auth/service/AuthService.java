package com.hris.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.auth.dto.LoginRequest;
import com.hris.auth.dto.LogoutRequest;
import com.hris.auth.dto.MeResponse;
import com.hris.auth.dto.MenuNode;
import com.hris.auth.dto.RefreshRequest;
import com.hris.auth.dto.TokenResponse;
import com.hris.auth.entity.AuthDataScope;
import com.hris.auth.entity.AuthRole;
import com.hris.auth.entity.AuthTenant;
import com.hris.auth.entity.AuthUser;
import com.hris.auth.mapper.AuthTenantMapper;
import com.hris.auth.mapper.AuthUserMapper;
import com.hris.auth.model.LoginUser;
import com.hris.common.audit.AuditLog;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证业务编排：登录 / 刷新 / 登出 / 当前用户 / 菜单。
 * 依赖只向下（service → mapper），不依赖 HTTP 对象。
 */
@Service
public class AuthService {

    private static final int STATUS_ENABLED = 1;

    private final AuthTenantMapper tenantMapper;
    private final AuthUserMapper userMapper;
    private final UserAuthService userAuthService;
    private final MenuService menuService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthTenantMapper tenantMapper, AuthUserMapper userMapper,
                       UserAuthService userAuthService, MenuService menuService,
                       TokenService tokenService, PasswordEncoder passwordEncoder) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.userAuthService = userAuthService;
        this.menuService = menuService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 账号密码登录完整链路：
     * <ol>
     *   <li>按 tenantCode 查 AuthTenant（平台级表，绕过租户插件），校验存在且状态=正常；</li>
     *   <li>设置 TraceContext.tenantId，按 username 查 AuthUser，校验存在且未禁用；</li>
     *   <li>Argon2id 校验密码，失败抛 20401（不区分"用户不存在"与"密码错误"防枚举）；</li>
     *   <li>加载用户角色/权限点构建 LoginUser，签发 access + refresh 令牌对。</li>
     * </ol>
     * 审计日志由 @AuditLog 切面输出。
     */
    @AuditLog(value = "登录", module = "auth")
    public TokenResponse login(LoginRequest req) {
        AuthTenant tenant = tenantMapper.findByCode(req.tenantCode().trim().toLowerCase());
        if (tenant == null) {
            throw new BizException(ErrorCode.TENANT_INVALID, "租户不存在");
        }
        if (tenant.getStatus() == null || tenant.getStatus() != STATUS_ENABLED) {
            throw new BizException(ErrorCode.TENANT_INVALID, "租户已停用或过期");
        }
        Long tenantId = tenant.getId();
        TraceContext.setTenantId(tenantId);
        try {
            AuthUser user = userMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                    .eq(AuthUser::getUsername, req.username().trim().toLowerCase()));
            if (user == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
            }
            if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "账号已禁用");
            }
            if (!passwordEncoder.matches(req.password(), user.getPwdHash())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
            }
            TraceContext.setUserId(user.getId());
            LoginUser loginUser = buildLoginUser(user, tenantId);
            String access = tokenService.issueAccess(loginUser);
            String refresh = tokenService.issueRefresh(user.getId(), tenantId);
            return TokenResponse.of(access, refresh,
                    tokenService.accessTtlSeconds(), tokenService.refreshTtlSeconds());
        } finally {
            TraceContext.setTenantId(null);
        }
    }

    /**
     * 刷新令牌：校验 refresh 签名/类型/未撤销/未重用 → 查用户状态 → 消费旧 jti（轮换）
     * → 重建 LoginUser → 签发新 access + 新 refresh。旧 refresh 再次使用会命中重用检测抛 401。
     */
    public TokenResponse refresh(RefreshRequest req) {
        TokenService.RefreshToken rt = tokenService.parseRefresh(req.refreshToken());
        TraceContext.setTenantId(rt.tenantId());
        try {
            AuthUser user = userMapper.selectById(rt.userId());
            if (user == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "账号不存在");
            }
            if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "账号已禁用");
            }
            tokenService.consumeRefresh(rt.jti());
            LoginUser loginUser = buildLoginUser(user, rt.tenantId());
            String access = tokenService.issueAccess(loginUser);
            String refresh = tokenService.issueRefresh(rt.userId(), rt.tenantId());
            return TokenResponse.of(access, refresh,
                    tokenService.accessTtlSeconds(), tokenService.refreshTtlSeconds());
        } finally {
            TraceContext.setTenantId(null);
        }
    }

    /**
     * 登出：把 access jti 加入黑名单（TTL 内即时失效），refresh jti 移除并加入黑名单。
     */
    public void logout(String accessToken, LogoutRequest req) {
        tokenService.revoke(accessToken, req == null ? null : req.refreshToken());
    }

    /**
     * 组装当前用户完整摘要：用户基本信息 + 角色列表 + 权限点集合 + 数据权限规则 + 租户品牌。
     * 数据权限按角色 ID 反查 roleCode 后返回。
     */
    public MeResponse me(LoginUser current) {
        TraceContext.setTenantId(current.tenantId());
        try {
            AuthUser user = userMapper.selectById(current.userId());
            if (user == null || user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "账号已失效");
            }
            List<AuthRole> roles = userAuthService.rolesOf(user.getId());
            List<String> perms = userAuthService.permsOf(user.getId());
            List<AuthDataScope> scopes = userAuthService.dataScopesOf(user.getId());
            AuthTenant tenant = tenantMapper.findByIdIgnoreTenant(current.tenantId());

            MeResponse.UserInfo ui = new MeResponse.UserInfo(
                    user.getId(), user.getEmployeeId(), user.getUsername(), user.getStatus());
            List<MeResponse.RoleInfo> roleInfos = roles.stream()
                    .map(r -> new MeResponse.RoleInfo(r.getCode(), r.getName()))
                    .toList();
            List<MeResponse.DataScopeInfo> scopeInfos = scopes.stream()
                    .map(s -> new MeResponse.DataScopeInfo(roleCodeOf(roles, s),
                            s.getScopeType(), s.getDeptIds()))
                    .toList();
            MeResponse.TenantBrand brand = tenant == null ? null : new MeResponse.TenantBrand(
                    tenant.getId(), tenant.getCode(), tenant.getName(), tenant.getPlan(),
                    tenant.getTimezone(), tenant.getDomain());
            return new MeResponse(ui, roleInfos, perms, scopeInfos, brand);
        } finally {
            TraceContext.setTenantId(null);
        }
    }

    /**
     * 按当前用户权限点集合派生菜单树（模块=一级、资源=二级、动作=按钮）。
     */
    public List<MenuNode> menus(LoginUser current) {
        List<String> perms = userAuthService.permsOf(current.userId());
        return menuService.build(perms);
    }

    /**
     * 根据 AuthUser + tenantId 构建 LoginUser：加载角色编码集合与权限点集合，
     * 供 JWT claims 写入与 AuthContext 使用。
     */
    private LoginUser buildLoginUser(AuthUser user, Long tenantId) {
        List<String> roleCodes = userAuthService.rolesOf(user.getId()).stream()
                .map(AuthRole::getCode).toList();
        List<String> perms = userAuthService.permsOf(user.getId());
        return new LoginUser(user.getId(), tenantId, user.getEmployeeId(),
                user.getUsername(), user.getStatus(), roleCodes, perms);
    }

    /**
     * 按数据权限规则的 roleId 反查角色编码，用于 MeResponse 输出 roleCode 字段。
     */
    private String roleCodeOf(List<AuthRole> roles, AuthDataScope scope) {
        for (AuthRole role : roles) {
            if (role.getId().equals(scope.getRoleId())) {
                return role.getCode();
            }
        }
        return null;
    }
}

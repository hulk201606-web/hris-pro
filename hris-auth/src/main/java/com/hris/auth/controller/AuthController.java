package com.hris.auth.controller;

import com.hris.auth.config.AuthContext;
import com.hris.auth.dto.LoginRequest;
import com.hris.auth.dto.LogoutRequest;
import com.hris.auth.dto.MeResponse;
import com.hris.auth.dto.MenuNode;
import com.hris.auth.dto.MfaDtos.MfaBindResponse;
import com.hris.auth.dto.MfaDtos.MfaVerifyRequest;
import com.hris.auth.dto.RefreshRequest;
import com.hris.auth.dto.TokenResponse;
import com.hris.auth.model.LoginUser;
import com.hris.auth.service.AuthService;
import com.hris.auth.service.MfaService;
import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证端点（登录/刷新/登出/当前用户/菜单/MFA）。仅做参数校验与响应组装，业务下沉 service。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final MfaService mfaService;

    public AuthController(AuthService authService, MfaService mfaService) {
        this.authService = authService;
        this.mfaService = mfaService;
    }

    /**
     * 账号密码登录（公开端点，白名单放行）。
     * 入参：租户编码 + 用户名 + 密码；成功返回 access/refresh 令牌对。
     */
    @PostMapping("/login")
    public R<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return R.ok(authService.login(req));
    }

    /**
     * 刷新令牌（公开端点，白名单放行）：用 refresh 令牌换新 access + 新 refresh（轮换）。
     */
    @PostMapping("/refresh")
    public R<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return R.ok(authService.refresh(req));
    }

    /**
     * 登出：撤销当前 access 令牌（黑名单）与可选的 refresh 令牌（移除并黑名单）。
     */
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                          @RequestBody(required = false) LogoutRequest req) {
        authService.logout(extractToken(authorization), req);
        return R.ok();
    }

    /**
     * 查询当前登录用户摘要：用户信息 + 角色 + 权限点 + 数据权限 + 租户品牌。需登录。
     */
    @GetMapping("/me")
    public R<MeResponse> me() {
        return R.ok(authService.me(requireLogin()));
    }

    /**
     * 查询当前用户可见菜单树（由权限点 模块:资源:动作 派生）。需登录。
     */
    @GetMapping("/menus")
    public R<List<MenuNode>> menus() {
        return R.ok(authService.menus(requireLogin()));
    }

    /**
     * 绑定 MFA（TOTP）：生成 Base32 密钥并写入 auth_user.mfa_secret，返回 otpauth URI 供扫码。需登录。
     */
    @PostMapping("/mfa/bind")
    public R<MfaBindResponse> bindMfa() {
        return R.ok(mfaService.bind(requireLogin()));
    }

    /**
     * 校验 MFA 动态码（敏感操作二次认证用）。需登录，未绑定或码错误抛 20410。
     */
    @PostMapping("/mfa/verify")
    public R<Void> verifyMfa(@Valid @RequestBody MfaVerifyRequest req) {
        mfaService.verify(requireLogin(), req.code());
        return R.ok();
    }

    /**
     * 从 AuthContext 取当前登录用户，未登录则抛 401 UNAUTHORIZED。
     */
    private LoginUser requireLogin() {
        LoginUser user = AuthContext.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未认证或凭证已失效");
        }
        return user;
    }

    /**
     * 从 Authorization 头提取 Bearer 令牌原文，无 Bearer 前缀返回 null。
     */
    private String extractToken(String header) {
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}

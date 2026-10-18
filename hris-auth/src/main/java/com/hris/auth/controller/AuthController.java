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

    @PostMapping("/login")
    public R<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return R.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public R<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return R.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                          @RequestBody(required = false) LogoutRequest req) {
        authService.logout(extractToken(authorization), req);
        return R.ok();
    }

    @GetMapping("/me")
    public R<MeResponse> me() {
        return R.ok(authService.me(requireLogin()));
    }

    @GetMapping("/menus")
    public R<List<MenuNode>> menus() {
        return R.ok(authService.menus(requireLogin()));
    }

    @PostMapping("/mfa/bind")
    public R<MfaBindResponse> bindMfa() {
        return R.ok(mfaService.bind(requireLogin()));
    }

    @PostMapping("/mfa/verify")
    public R<Void> verifyMfa(@Valid @RequestBody MfaVerifyRequest req) {
        mfaService.verify(requireLogin(), req.code());
        return R.ok();
    }

    private LoginUser requireLogin() {
        LoginUser user = AuthContext.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未认证或凭证已失效");
        }
        return user;
    }

    private String extractToken(String header) {
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}

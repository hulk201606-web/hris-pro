package com.hris.auth.service;

import com.hris.auth.dto.MfaDtos.MfaBindResponse;
import com.hris.auth.entity.AuthUser;
import com.hris.auth.mapper.AuthUserMapper;
import com.hris.auth.model.LoginUser;
import com.hris.auth.util.TotpUtil;
import com.hris.common.audit.AuditLog;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * MFA（TOTP）绑定与校验（docs/08 §1）。绑定即写入 mfa_secret 启用；校验用于
 * 敏感操作二次认证（薪酬/导出等，本增量仅提供能力，登录逐步认证在后续接入）。
 */
@Service
public class MfaService {

    private static final String ISSUER = "hris";

    private final AuthUserMapper userMapper;

    public MfaService(AuthUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 绑定 MFA：生成 20 字节随机 Base32 密钥写入 auth_user.mfa_secret，
     * 返回密钥原文与 otpauth:// URI（供 Google Authenticator 扫码）。
     */
    @AuditLog(value = "绑定 MFA", module = "auth")
    public MfaBindResponse bind(LoginUser current) {
        AuthUser user = requireUser(current.userId());
        String secret = TotpUtil.generateSecret();
        user.setMfaSecret(secret);
        userMapper.updateById(user);
        String url = TotpUtil.otpauthUrl(secret, ISSUER, user.getUsername());
        return new MfaBindResponse(secret, url);
    }

    /**
     * 校验 TOTP 动态码：未绑定抛 20410 SECOND_AUTH_REQUIRED，码错误抛 20410。
     * 用于薪酬/导出等敏感操作的二次认证。
     */
    public void verify(LoginUser current, String code) {
        AuthUser user = requireUser(current.userId());
        if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
            throw new BizException(ErrorCode.SECOND_AUTH_REQUIRED, "尚未绑定 MFA");
        }
        if (!TotpUtil.verify(user.getMfaSecret(), code)) {
            throw new BizException(ErrorCode.SECOND_AUTH_REQUIRED, "TOTP 验证码错误");
        }
    }

    /** 按 userId 查用户，不存在抛 20401 UNAUTHORIZED。 */
    private AuthUser requireUser(Long userId) {
        AuthUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号不存在");
        }
        return user;
    }
}

package com.hris.auth.service;

import com.hris.auth.config.AuthProperties;
import com.hris.auth.model.LoginUser;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.JwtSecretGuard;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT 签发 / 校验 / 撤销（HS256）。
 *
 * <p>MVP 简化：refresh 令牌轮换与登出黑名单用进程内内存实现，单实例可运行；
 * 多实例部署需替换为 Redis（键前缀 t{tenantId}:，见 docs/08 §3），仅替换存储实现即可。</p>
 */
@Service
public class TokenService {

    private static final String CLAIM_TENANT_ID = "tenant_id";
    private static final String CLAIM_EMP_ID = "emp_id";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_STATUS = "status";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMS = "perms";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final String issuer;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    /** refresh jti → 过期时间（轮换 + 重用检测） */
    private final Map<String, Long> refreshStore = new ConcurrentHashMap<>();

    /** 撤销 jti → 过期时间（登出/禁用即时失效） */
    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    public TokenService(AuthProperties props) {
        this.key = Keys.hmacShaKeyFor(
                JwtSecretGuard.requireStrongSecret(props.getSecret()).getBytes(StandardCharsets.UTF_8));
        this.issuer = props.getIssuer();
        this.accessTtlSeconds = props.getAccessTtlSeconds();
        this.refreshTtlSeconds = props.getRefreshTtlSeconds();
    }

    /**
     * 签发 access 令牌（HS256，默认 15 分钟）：
     * claims 含 sub=userId、tenant_id、emp_id、username、status、roles、perms、typ=access。
     * 显式固定 HS256 算法，避免密钥轮换后 jjwt 按长度自动切到 HS384/512。
     */
    public String issueAccess(LoginUser user) {
        Date now = new Date();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.userId()))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_TENANT_ID, user.tenantId())
                .claim(CLAIM_EMP_ID, user.employeeId())
                .claim(CLAIM_USERNAME, user.username())
                .claim(CLAIM_STATUS, user.status())
                .claim(CLAIM_ROLES, user.roles())
                .claim(CLAIM_PERMS, user.perms())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTtlSeconds * 1000))
                // 显式固定 HS256：不指定算法时 jjwt 会按密钥长度自动选 HS256/384/512，
                // 导致轮换密钥后 alg 静默变化，且与 docs/08 的安全设计描述不一致。
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 签发 refresh 令牌（HS256，默认 7 天）：claims 仅含 sub/userId、tenant_id、typ=refresh、jti。
     * jti 同时写入进程内 refreshStore（ConcurrentHashMap），用于轮换与重用检测。
     */
    public String issueRefresh(Long userId, Long tenantId) {
        Date now = new Date();
        long exp = now.getTime() + refreshTtlSeconds * 1000;
        String jti = UUID.randomUUID().toString();
        refreshStore.put(jti, exp);
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .id(jti)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .claim(CLAIM_TENANT_ID, tenantId)
                .issuedAt(now)
                .expiration(new Date(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析 access 令牌：验签 + issuer + 类型校验 + 黑名单检查。
     * 黑名单命中（已登出/已禁用）抛 20401 UNAUTHORIZED。
     */
    public LoginUser parseAccess(String token) {
        Claims c = parse(token);
        if (!TYPE_ACCESS.equals(c.get(CLAIM_TYPE, String.class))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "令牌类型错误");
        }
        String jti = c.getId();
        if (jti != null && blacklist.containsKey(jti)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "令牌已失效");
        }
        return toLoginUser(c);
    }

    /**
     * 校验 refresh 令牌（签名/类型/未撤销/未重用），返回其主体信息供业务层重建用户。
     */
    public RefreshToken parseRefresh(String refreshToken) {
        Claims c = parse(refreshToken);
        if (!TYPE_REFRESH.equals(c.get(CLAIM_TYPE, String.class))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "令牌类型错误");
        }
        String jti = c.getId();
        if (jti == null || !refreshStore.containsKey(jti) || blacklist.containsKey(jti)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "刷新令牌已失效或已被重用");
        }
        Long userId = Long.parseLong(c.getSubject());
        Long tenantId = ((Number) c.get(CLAIM_TENANT_ID)).longValue();
        return new RefreshToken(userId, tenantId, jti);
    }

    /** 轮换时消费旧 refresh（使其不可再次使用）。 */
    public void consumeRefresh(String jti) {
        refreshStore.remove(jti);
    }

    /** 撤销：access（黑名单）+ refresh（移除并黑名单）。 */
    public void revoke(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                Claims c = parse(accessToken);
                String jti = c.getId();
                if (jti != null) {
                    blacklist.put(jti, c.getExpiration().getTime());
                }
            } catch (BizException ignore) {
                // 已过期或无效，忽略
            }
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                Claims c = parse(refreshToken);
                String jti = c.getId();
                if (jti != null) {
                    refreshStore.remove(jti);
                    blacklist.put(jti, c.getExpiration().getTime());
                }
            } catch (BizException ignore) {
                // 已过期或无效，忽略
            }
        }
    }

    /** @return access 令牌有效期（秒），供 TokenResponse.expiresIn 返回前端 */
    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }

    /** @return refresh 令牌有效期（秒），供 TokenResponse.refreshExpiresIn 返回前端 */
    public long refreshTtlSeconds() {
        return refreshTtlSeconds;
    }

    /**
     * 底层解析：HS256 验签 + issuer 校验，JwtException/IllegalArgumentException 统一包装为
     * BizException(20401 UNAUTHORIZED)，避免把 jjwt 内部异常类型暴露给上层。
     */
    private Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "令牌无效或已过期");
        }
    }

    /**
     * 把 JWT claims 反序列化为 LoginUser：Number 类型 claims 安全转 Long/Integer，
     * roles/perms 经 castStrings 转 List<String>。
     */
    private LoginUser toLoginUser(Claims c) {
        Long userId = Long.parseLong(c.getSubject());
        Long tenantId = ((Number) c.get(CLAIM_TENANT_ID)).longValue();
        Long empId = c.get(CLAIM_EMP_ID) == null ? null
                : ((Number) c.get(CLAIM_EMP_ID)).longValue();
        Integer status = c.get(CLAIM_STATUS) == null ? null
                : ((Number) c.get(CLAIM_STATUS)).intValue();
        return new LoginUser(userId, tenantId, empId,
                c.get(CLAIM_USERNAME, String.class), status,
                castStrings(c.get(CLAIM_ROLES)), castStrings(c.get(CLAIM_PERMS)));
    }

    /**
     * 安全地把 claims 中的 List 值转为 List<String>，非 List 时返回空集合。
     */
    @SuppressWarnings("unchecked")
    private List<String> castStrings(Object v) {
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * refresh 令牌主体信息。
     */
    public record RefreshToken(Long userId, Long tenantId, String jti) {
    }
}

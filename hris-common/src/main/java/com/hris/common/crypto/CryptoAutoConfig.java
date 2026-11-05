package com.hris.common.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 敏感列加密装配（ADR-007）。所有服务经 {@code scanBasePackages="com.hris"} 自动生效。
 *
 * <p>密钥策略（照 JwtSecretGuard 启动校验范式）：</p>
 * <ul>
 *   <li>HRIS_CRYPTO_KEY 已配置：经 {@link CryptoKeyGuard#requireStrongKey} 强校验，
 *       弱密钥/非法值直接抛异常拒绝启动；</li>
 *   <li>未配置：进入迁移期明文兼容模式（读写透传，行为与加密上线前一致），
 *       保证存量单测与本地开发零依赖；生产部署设 HRIS_CRYPTO_REQUIRED=true 强制必填，
 *       缺失即拒绝启动。</li>
 * </ul>
 */
@Configuration
public class CryptoAutoConfig {

    private static final Logger log = LoggerFactory.getLogger(CryptoAutoConfig.class);

    @Bean
    public CryptoService cryptoService(Environment env) {
        String base64Key = env.getProperty("HRIS_CRYPTO_KEY", "");
        if (base64Key.isBlank()) {
            boolean required = Boolean.parseBoolean(env.getProperty("HRIS_CRYPTO_REQUIRED", "false"));
            if (required) {
                throw new IllegalStateException(
                        "HRIS_CRYPTO_REQUIRED=true 但未配置 HRIS_CRYPTO_KEY，拒绝启动（需 32 字节随机密钥的 Base64）");
            }
            log.warn("HRIS_CRYPTO_KEY 未配置，敏感列加密未启用（迁移期明文兼容模式，读写透传）");
            CryptoService svc = CryptoService.disabled();
            CryptoService.setInstance(svc);
            return svc;
        }
        byte[] key = CryptoKeyGuard.requireStrongKey(base64Key);
        CryptoService svc = new CryptoService(key);
        CryptoService.setInstance(svc);
        log.info("敏感列加密已启用（AES-256-GCM 信封加密，新写前缀 {}，存量只读前缀 {}，keyId={}）",
                CryptoService.PREFIX_V2, CryptoService.PREFIX, svc.currentKeyId());
        return svc;
    }
}

package com.hris.common.crypto;

import javax.crypto.SecretKey;

/**
 * 信封加密的 KMS 抽象（排期批 12 #30）。
 *
 * <p>信封加密（envelope encryption）分两层：KMS 持有不落盘的<b>主密钥</b>（KEK），
 * 业务真正用来加密数据的是<b>数据密钥</b>（DEK）。DEK 由 KMS 产生、可用 KEK 封装
 * （{@link #wrapKey}）后落库/落配置，解封（{@link #unwrapKey}）只发生在进程内。
 * 这样主密钥轮换时只需重封 DEK，不必重加密整库数据。</p>
 *
 * <p>本接口同时服务两种实现：{@link LocalKmsProvider}（自托管主密钥，当前默认）
 * 与云 KMS（阿里云 KMS / AWS KMS / Vault Transit，见 LocalKmsProvider 头部注释的
 * 预留实现说明）。</p>
 *
 * <p><b>错误语义</b>：{@link #unwrapKey} 在 keyId 与当前主密钥不匹配（主密钥已轮换
 * 且旧密钥未保留）时返回 {@code null}，由调用方 {@link CryptoService} 显式抛错；
 * 绝不以空密钥继续解密——静默降级会把密文当明文下发给业务层。</p>
 */
public interface KmsProvider {

    /** 数据密钥与其版本标识。 */
    record DataKey(String keyId, SecretKey key) {
    }

    /**
     * 取得当前版本的数据密钥（全局单 DEK，见 {@link CryptoService} 说明）。
     * 实现可缓存以避免每次派生/调用远端 KMS。
     */
    DataKey generateDataKey();

    /**
     * 用主密钥封装（wrap）数据密钥，返回可安全落盘的密封串。
     * 本地实现返回 base64(iv||ct||tag)；云 KMS 实现返回远端返回的 ciphertextBlob。
     */
    String wrapKey(SecretKey dataKey);

    /**
     * 解封数据密钥。{@code keyId} 与当前主密钥不匹配时返回 {@code null}（版本化语义）。
     *
     * @param wrappedKey 封装串；本地实现允许为空（DEK 由主密钥确定性派生，enc2 密文只存 keyId）
     * @param keyId      密文携带的密钥版本标识
     */
    SecretKey unwrapKey(String wrappedKey, String keyId);

    /** 当前主密钥对应的 keyId（十六进制，8 字节）。 */
    String currentKeyId();
}

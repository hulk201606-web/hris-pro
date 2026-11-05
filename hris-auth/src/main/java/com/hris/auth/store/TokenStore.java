package com.hris.auth.store;

/**
 * 令牌存储抽象：refresh 轮换/重用检测 + 登出黑名单（docs/19 §10 生产化收尾）。
 *
 * <p>多实例部署时进程内 Map 会导致刷新/登出只在单实例生效，须切 Redis 实现；
 * 由 {@code hris.auth.token-store: memory|redis} 选择，缺省 memory（测试零外部依赖）。</p>
 */
public interface TokenStore {

    /**
     * 登记 refresh jti，exp 为过期时间（epoch 毫秒）。实现须以其推算 TTL。
     */
    void putRefresh(String jti, long expEpochMs);

    /** refresh jti 是否仍有效（未过期且未被轮换消费）。 */
    boolean existsRefresh(String jti);

    /** 轮换时消费旧 refresh，使其不可再次使用。 */
    void removeRefresh(String jti);

    /** 拉黑 jti（登出/禁用即时失效），ttlMs 为剩余有效期毫秒。 */
    void blacklist(String jti, long ttlMs);

    /** jti 是否已被拉黑。 */
    boolean isBlacklisted(String jti);
}

package com.hris.auth.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内 TokenStore：原 TokenService 的 ConcurrentHashMap 逻辑原样迁移。
 *
 * <p>仅用于单测与单机兜底；多实例部署必须切 {@link RedisTokenStore}
 * （hris.auth.token-store=redis），否则刷新/登出只在本实例生效。</p>
 */
public class InMemoryTokenStore implements TokenStore {

    /** refresh jti → 过期时间（轮换 + 重用检测） */
    private final Map<String, Long> refreshStore = new ConcurrentHashMap<>();

    /** 撤销 jti → 过期时间（登出/禁用即时失效） */
    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    @Override
    public void putRefresh(String jti, long expEpochMs) {
        refreshStore.put(jti, expEpochMs);
    }

    @Override
    public boolean existsRefresh(String jti) {
        return refreshStore.containsKey(jti);
    }

    @Override
    public void removeRefresh(String jti) {
        refreshStore.remove(jti);
    }

    @Override
    public void blacklist(String jti, long ttlMs) {
        if (ttlMs <= 0) {
            return;
        }
        blacklist.put(jti, System.currentTimeMillis() + ttlMs);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return blacklist.containsKey(jti);
    }
}

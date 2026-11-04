package com.hris.gateway.auth;

import com.hris.common.api.R;
import com.hris.gateway.config.AuthClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关集中查权限服务：验签后经 {@code lb://hris-auth} 直连内部端点实时查询当前用户 perms。
 *
 * <p>带短 TTL 本地缓存（默认 5 分钟），避免每个请求都打一次 auth 的 DB 查询。
 * 查询失败（不可达/超时/非 0）时降级返回空列表并告警，不让请求 500——
 * 下游 PermAspect 对空 perms 返回 20403，符合 fail-closed 预期。</p>
 */
@Service
public class PermQueryService {

    private static final Logger log = LoggerFactory.getLogger(PermQueryService.class);

    /** auth 服务名（经 @LoadBalanced WebClient 解析）。 */
    private static final String AUTH_SERVICE_URL = "lb://hris-auth";

    /** 查询超时，超时视同失败降级。 */
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(3);

    /** 空权限列表（降级/无权限时的统一返回）。 */
    private static final List<String> EMPTY_PERMS = List.of();

    private final WebClient.Builder webClientBuilder;
    private final AuthClientProperties props;

    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    public PermQueryService(WebClient.Builder webClientBuilder, AuthClientProperties props) {
        this.webClientBuilder = webClientBuilder;
        this.props = props;
    }

    /**
     * 查 userId 的权限点列表：命中未过期缓存直接返回，否则直连 auth 内部端点查询。
     * tenantId 透传给端点以还原租户上下文（auth_user_role / auth_role_perm 受租户插件拦截）。
     */
    public Mono<List<String>> permsOf(Long userId, Long tenantId) {
        CacheEntry hit = cache.get(userId);
        if (hit != null && hit.expireAt() > System.currentTimeMillis()) {
            return Mono.just(hit.perms());
        }
        return queryRemote(userId, tenantId);
    }

    private Mono<List<String>> queryRemote(Long userId, Long tenantId) {
        // 拼接完整 URL：lb://hris-auth + 内部端点路径 + {userId} 替换 + tenantId 查询参数。
        // 用字符串拼接而非 uriBuilder.path()，避免 path() 把 "lb://" 当作文本路径段转义。
        String uri = AUTH_SERVICE_URL + props.getPermsPath()
                .replace("{userId}", String.valueOf(userId))
                + "?tenantId=" + tenantId;
        return webClientBuilder.build()
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<R<List<String>>>() {
                })
                .timeout(QUERY_TIMEOUT)
                .map(r -> r.getCode() == 0 && r.getData() != null ? r.getData() : EMPTY_PERMS)
                .doOnNext(perms -> cache.put(userId, new CacheEntry(perms,
                        System.currentTimeMillis() + props.getCacheTtlSeconds() * 1000)))
                .onErrorResume(e -> {
                    log.warn("query perms failed, userId={}, tenantId={}, err={}",
                            userId, tenantId, e.getMessage());
                    return Mono.just(EMPTY_PERMS);
                });
    }

    private record CacheEntry(List<String> perms, long expireAt) {
    }
}

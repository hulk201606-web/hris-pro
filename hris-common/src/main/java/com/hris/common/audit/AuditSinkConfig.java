package com.hris.common.audit;

import com.hris.common.client.PlatformAuditClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AuditSink 装配（docs/19 §8.7-2）。
 *
 * <p>默认实现：经 {@link PlatformAuditClient} Feign 调 platform 落库。仅当容器内注册了
 * 该 Feign 客户端（服务在 @EnableFeignClients 扫描 com.hris.common.client）时生效；
 * 否则返回 null（NullBean），切面探测不到 Sink，保持 logback 输出。</p>
 *
 * <p>platform 自身提供直连 AuditLogService 的本地 Sink（@Primary），此处装配自动让位，
 * 不会出现 Feign 自调用。</p>
 */
@Configuration
public class AuditSinkConfig {

    @Bean
    public AuditSink feignAuditSink(ObjectProvider<PlatformAuditClient> clientProvider) {
        PlatformAuditClient client = clientProvider.getIfAvailable();
        return client == null ? null : client::append;
    }
}

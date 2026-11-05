package com.hris.employee.client;

import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * 自助聚合 Feign 超时（SELF-F-001：整体 &lt;2s）。
 *
 * <p>不加 @Configuration：只作为 @FeignClient(configuration=...) 的子上下文配置，
 * 避免 1s 超时污染其它域的长耗时调用。连接/读均 1s，配合并行聚合，最慢子项不会
 * 拖垮整体响应。</p>
 */
public class SelfFeignConfig {

    @Bean
    public Request.Options selfFeignOptions() {
        return new Request.Options(1000, TimeUnit.MILLISECONDS,
                1000, TimeUnit.MILLISECONDS, true);
    }
}

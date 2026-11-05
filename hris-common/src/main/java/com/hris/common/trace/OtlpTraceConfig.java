package com.hris.common.trace;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * OTLP tracing 导出装配（docs/21 批 5 D-10：Micrometer → OpenTelemetry）。
 *
 * <p>依赖（hris-common pom，随 BOM）：micrometer-tracing-bridge-otel +
 * opentelemetry-exporter-otlp。桥接类在 classpath 时 Spring Boot Actuator 的
 * OpenTelemetry 自动配置即生效：Tracer 可用、span 在内存中记录。</p>
 *
 * <p>导出器策略：仅当环境变量 {@code OTEL_EXPORTER_OTLP_ENDPOINT} 非空时注册
 * {@link OtlpHttpSpanExporter}（Boot 自动把它挂入 SdkTracerProvider 的 BatchSpanProcessor）；
 * 未配置时不注册任何导出器——无 collector 环境下仅内存 span、零报错，服务启动即生效。
 * 需要导出时只加环境变量，无需改代码或配置文件。</p>
 */
@Configuration
@ConditionalOnClass(OtlpHttpSpanExporter.class)
public class OtlpTraceConfig {

    private static final Logger log = LoggerFactory.getLogger(OtlpTraceConfig.class);

    /** 环境变量 OTEL_EXPORTER_OTLP_ENDPOINT 非空才装配导出器。 */
    static class OtlpEndpointPresent implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String endpoint = context.getEnvironment().getProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "");
            return endpoint != null && !endpoint.isBlank();
        }
    }

    @Bean
    @ConditionalOnMissingBean(OtlpHttpSpanExporter.class)
    @org.springframework.context.annotation.Conditional(OtlpEndpointPresent.class)
    public OtlpHttpSpanExporter otlpHttpSpanExporter(Environment env) {
        String endpoint = env.getProperty("OTEL_EXPORTER_OTLP_ENDPOINT");
        log.info("OTLP tracing 导出已启用，endpoint={}", endpoint);
        return OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint)
                .build();
    }
}

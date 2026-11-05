package com.hris.common.trace;

import io.micrometer.tracing.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-10 tracing 接入验证：桥接 + 自动配置在无 collector 环境（未配置
 * OTEL_EXPORTER_OTLP_ENDPOINT）下启动即生效——Tracer/SdkTracerProvider Bean 在位、
 * 不注册导出器、无报错；配置 endpoint 后导出器 Bean 出现并被挂入导出链。
 */
class OtlpTraceConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OpenTelemetrySdkAutoConfiguration.class,
                    OpenTelemetryTracingAutoConfiguration.class,
                    OtlpTraceConfig.class));

    @Test
    void noEndpointMeansInMemorySpansNoExporter() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(Tracer.class);
            assertThat(ctx).hasSingleBean(SdkTracerProvider.class);
            assertThat(ctx).doesNotHaveBean(OtlpHttpSpanExporter.class);
        });
    }

    @Test
    void endpointRegistersOtlpExporter() {
        runner.withPropertyValues("OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:4318")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(OtlpHttpSpanExporter.class);
                    assertThat(ctx).hasSingleBean(Tracer.class);
                    assertThat(ctx).hasSingleBean(SdkTracerProvider.class);
                });
    }

    @Test
    void blankEndpointMeansNoExporter() {
        runner.withPropertyValues("OTEL_EXPORTER_OTLP_ENDPOINT=")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(OtlpHttpSpanExporter.class);
                });
    }
}

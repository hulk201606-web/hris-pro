package com.hris.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * webhook 真实投递（PLT-F-003 深化）：java.net.http.HttpClient 同步 POST JSON，5 秒超时。
 *
 * <p>失败统一收敛为 {@link WebhookPoster.PostResult#fail}（网络异常/非 2xx），
 * 不向调用方抛异常——测试发送的失败要以摘要返回而非 500。</p>
 */
@Component
public class HttpWebhookPoster implements WebhookPoster {

    private static final Logger log = LoggerFactory.getLogger(HttpWebhookPoster.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client;

    public HttpWebhookPoster() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public PostResult postJson(String url, String json) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json == null ? "" : json))
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = resp.body() == null ? "" : truncate(resp.body());
            if (resp.statusCode() / 100 == 2) {
                return PostResult.ok(resp.statusCode(), body);
            }
            return PostResult.fail(resp.statusCode(), body, "webhook 返回非 2xx: HTTP " + resp.statusCode());
        } catch (Exception e) {
            log.warn("webhook post failed, url={}: {}", url, e.getMessage());
            return PostResult.fail(0, null, "webhook 调用异常: " + e.getClass().getSimpleName());
        }
    }

    /** 响应体截断，避免超长响应撑爆记录与日志。 */
    private String truncate(String body) {
        return body.length() <= 500 ? body : body.substring(0, 500);
    }
}

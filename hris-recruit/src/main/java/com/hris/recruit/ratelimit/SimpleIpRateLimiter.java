package com.hris.recruit.ratelimit;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 官网公开端点内存 IP 限流器（RCS-F-004 配套，MVP 级）。
 *
 * <p>语义：同一 IP 在滑动 60 秒窗口内最多 10 次，超出返回 false（调用方转 RATE_LIMITED 业务码）。</p>
 *
 * <p><b>MVP 局限（报告注明）：</b>单机内存态，服务多实例时不共享计数（限流阈值按实例数放大）；
 * 无持久化，重启清零；仅作防刷第一道闸，后续应迁移到网关 Sentinel IP 维度或 Redis 滑窗。</p>
 */
@Component
public class SimpleIpRateLimiter {

    /** 窗口长度（毫秒）。 */
    static final long WINDOW_MILLIS = 60_000L;
    /** 窗口内允许的最大请求数。 */
    static final int MAX_REQUESTS = 10;

    /** ip -> 窗口内请求时间戳队列。 */
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * 尝试为 key（客户端 IP）获取一次配额。
     *
     * @return true=放行；false=窗口内已超限
     */
    public boolean tryAcquire(String key) {
        if (key == null || key.isBlank()) {
            // 无法识别来源时不限流（fail-open），避免误伤
            return true;
        }
        long now = System.currentTimeMillis();
        Deque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() >= WINDOW_MILLIS) {
                queue.pollFirst();
            }
            if (queue.size() >= MAX_REQUESTS) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }
}

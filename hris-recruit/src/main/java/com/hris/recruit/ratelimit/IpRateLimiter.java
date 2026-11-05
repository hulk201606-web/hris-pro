package com.hris.recruit.ratelimit;

/**
 * 官网公开端点 IP 限流器契约（批 7 #12 由内存滑窗迁移为 Redis 固定窗口）。
 */
public interface IpRateLimiter {

    /**
     * 尝试为 key（客户端 IP）获取一次配额。
     *
     * @return true=放行；false=窗口内已超限（调用方转 RATE_LIMITED 业务码）
     */
    boolean tryAcquire(String key);
}

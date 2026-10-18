package com.hris.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关运行参数：白名单、内部端点标识、CORS、请求体大小、Sentinel 端点限流规则。
 */
@Component
@ConfigurationProperties(prefix = "hris.gateway")
public class HrisGatewayProperties {

    /** 白名单路径（AntPathMatcher 语义），命中则跳过 JWT 校验。 */
    private List<String> whitelist = new ArrayList<>();

    /** 内部端点路径标识，路径包含该子串即拒绝公网访问。 */
    private String internalPathMarker = "/internal/";

    /** 请求体大小上限（字节），超限返回 413。 */
    private long maxBodySizeBytes = 10 * 1024 * 1024L;

    private Cors cors = new Cors();

    private RateLimit rateLimit = new RateLimit();

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }

    public String getInternalPathMarker() {
        return internalPathMarker;
    }

    public void setInternalPathMarker(String internalPathMarker) {
        this.internalPathMarker = internalPathMarker;
    }

    public long getMaxBodySizeBytes() {
        return maxBodySizeBytes;
    }

    public void setMaxBodySizeBytes(long maxBodySizeBytes) {
        this.maxBodySizeBytes = maxBodySizeBytes;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class Cors {

        private List<String> origins = new ArrayList<>();
        private List<String> methods = new ArrayList<>();
        private List<String> headers = new ArrayList<>();
        private boolean allowCredentials = true;
        private long maxAgeSeconds = 86400L;

        public List<String> getOrigins() {
            return origins;
        }

        public void setOrigins(List<String> origins) {
            this.origins = origins;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public void setHeaders(List<String> headers) {
            this.headers = headers;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public long getMaxAgeSeconds() {
            return maxAgeSeconds;
        }

        public void setMaxAgeSeconds(long maxAgeSeconds) {
            this.maxAgeSeconds = maxAgeSeconds;
        }
    }

    public static class RateLimit {

        private boolean enabled = false;
        private List<Rule> rules = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<Rule> getRules() {
            return rules;
        }

        public void setRules(List<Rule> rules) {
            this.rules = rules;
        }
    }

    public static class Rule {

        /** 匹配的请求路径（Sentinel 资源名，即原始请求路径）。 */
        private String path;

        /** 每秒允许通过的请求数（QPS）。 */
        private double qps;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public double getQps() {
            return qps;
        }

        public void setQps(double qps) {
            this.qps = qps;
        }
    }
}

package com.hris.recruit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * hris-recruit 配置属性（前缀 hris.recruit）。
 *
 * <p>ai.dashscope：简历 AI 解析走 DashScope OpenAI 兼容端点；api-key 从环境变量
 * DASHSCOPE_API_KEY 注入（yml 用 ${DASHSCOPE_API_KEY:} 占位，禁止写死 key）。
 * blacklist.fail-open：黑名单门卫降级开关，缺省 true 保持 fail-open 现状。</p>
 */
@ConfigurationProperties(prefix = "hris.recruit")
public class RecruitProperties {

    private final Blacklist blacklist = new Blacklist();
    private final Ai ai = new Ai();

    /** 简历保留天数（RCS-F-022，hris.recruit.resume-retention-days，缺省 180 天）。 */
    private int resumeRetentionDays = 180;

    public int getResumeRetentionDays() {
        return resumeRetentionDays;
    }

    public void setResumeRetentionDays(int resumeRetentionDays) {
        this.resumeRetentionDays = resumeRetentionDays;
    }

    public Blacklist getBlacklist() {
        return blacklist;
    }

    public Ai getAi() {
        return ai;
    }

    /** 黑名单门卫配置。 */
    public static class Blacklist {
        /** true=emp 不可用时放行（现状）；false=不可用时拒绝入库（fail-closed）。 */
        private boolean failOpen = true;

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }
    }

    /** 简历 AI 解析配置。 */
    public static class Ai {
        private final Dashscope dashscope = new Dashscope();

        public Dashscope getDashscope() {
            return dashscope;
        }
    }

    /** DashScope OpenAI 兼容端点配置。 */
    public static class Dashscope {
        /** OpenAI 兼容 base-url。 */
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        /** API key，来自环境变量 DASHSCOPE_API_KEY；空串表示未配置（调用时报错，不静默降级）。 */
        private String apiKey = "";
        /** 对话模型，来自 DASHSCOPE_CHAT_MODEL，缺省 qwen3.8-27b。 */
        private String chatModel = "qwen3.8-27b";
        /** LLM 调用超时（秒）。 */
        private int timeoutSeconds = 30;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}

package com.hris.platform.service;

/**
 * webhook 投递抽象（PLT-F-003 深化）。
 *
 * <p>独立接口便于单测 mock：真实实现 {@link HttpWebhookPoster} 用
 * java.net.http.HttpClient 同步 POST JSON；测试注入桩实现验证渲染结果与状态回写，
 * 不真实外呼。</p>
 */
public interface WebhookPoster {

    /** 投递结果摘要。 */
    record PostResult(boolean success, int httpStatus, String responseBody, String failedReason) {

        /** 构造成功结果。 */
        public static PostResult ok(int httpStatus, String body) {
            return new PostResult(true, httpStatus, body, null);
        }

        /** 构造失败结果（网络异常/非 2xx 统一收敛为失败，不向调用方抛异常）。 */
        public static PostResult fail(int httpStatus, String body, String reason) {
            return new PostResult(false, httpStatus, body, reason);
        }
    }

    /**
     * 同步 POST JSON 到指定 webhook。
     *
     * @param url    目标地址（调用方须先过 SSRF 校验）
     * @param json   请求体 JSON 文本
     * @return 投递结果摘要
     */
    PostResult postJson(String url, String json);
}

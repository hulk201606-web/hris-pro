package com.hris.recruit.client;

import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 平台通知能力 Feign 客户端（RCS-F-018 Offer 发送）。
 *
 * <p>契约：POST /internal/plt/notify，body {channel, recipient, subject, content, bizType, bizKey}；
 * 返回 data = {status: SENT|FAILED, error?: String}。平台侧内部端点尚未提供时调用失败，
 * 由 OfferService fail-closed 记 FAILED 并透出错误，绝不伪造 SENT。</p>
 */
@FeignClient(name = "hris-platform", path = "/internal/plt", contextId = "recruitNotifyClient")
public interface NotifyClient {

    @PostMapping("/notify")
    R<Map<String, Object>> notify(@RequestBody Map<String, Object> request);
}

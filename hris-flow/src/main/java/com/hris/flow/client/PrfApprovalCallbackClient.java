package com.hris.flow.client;

import com.hris.common.api.R;
import com.hris.flow.dto.ApprovalCallbackRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/**
 * 绩效域审批回调客户端（bizType 前缀 PRF_* → hris-performance）。
 *
 * <p>消费 hris-performance 的 {@code POST /internal/prf/approval-callback}
 * （PrfApprovalCallbackService 按申诉单状态幂等）。</p>
 */
@FeignClient(name = "hris-performance", path = "/internal/prf", contextId = "prfApprovalCallback")
public interface PrfApprovalCallbackClient {

    @PostMapping("/approval-callback")
    R<Map<String, Object>> callback(ApprovalCallbackRequest req);
}

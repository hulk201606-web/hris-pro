package com.hris.flow.client;

import com.hris.common.api.R;
import com.hris.flow.dto.ApprovalCallbackRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/**
 * 考勤域审批回调客户端（bizType 前缀 ATT_* → hris-attendance）。
 *
 * <p>消费 hris-attendance 的 {@code POST /internal/att/approval-callback}
 * （ApprovalService 按单据状态幂等：非 PROCESSING 返回 changed=false）。</p>
 */
@FeignClient(name = "hris-attendance", path = "/internal/att", contextId = "attApprovalCallback")
public interface AttApprovalCallbackClient {

    @PostMapping("/approval-callback")
    R<Map<String, Object>> callback(ApprovalCallbackRequest req);
}

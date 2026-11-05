package com.hris.flow.client;

import com.hris.common.api.R;
import com.hris.flow.dto.ApprovalCallbackRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/**
 * 薪酬域审批回调客户端（bizType 前缀 CMP_* → hris-compensation）。
 *
 * <p>消费 hris-compensation 的 {@code POST /internal/cmp/approval-callback}
 * （CompApprovalCallbackService 按算薪批次状态幂等）。</p>
 */
@FeignClient(name = "hris-compensation", path = "/internal/cmp", contextId = "cmpApprovalCallback")
public interface CmpApprovalCallbackClient {

    @PostMapping("/approval-callback")
    R<Map<String, Object>> callback(ApprovalCallbackRequest req);
}

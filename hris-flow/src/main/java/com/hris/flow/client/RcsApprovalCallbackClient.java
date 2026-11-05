package com.hris.flow.client;

import com.hris.common.api.R;
import com.hris.flow.dto.ApprovalCallbackRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/**
 * 招聘域审批回调客户端（bizType 前缀 RCS_* → hris-recruit）。
 *
 * <p>消费 hris-recruit 的 {@code POST /internal/rcs/approval-callback}
 * （RecruitApprovalCallbackService 按单据状态幂等）。</p>
 */
@FeignClient(name = "hris-recruit", path = "/internal/rcs", contextId = "rcsApprovalCallback")
public interface RcsApprovalCallbackClient {

    @PostMapping("/approval-callback")
    R<Map<String, Object>> callback(ApprovalCallbackRequest req);
}

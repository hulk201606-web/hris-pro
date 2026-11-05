package com.hris.flow.client;

import com.hris.common.api.R;
import com.hris.flow.dto.ApprovalCallbackRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/**
 * 组织域审批回调客户端（bizType 前缀 ORG_* → hris-org）。
 *
 * <p>消费 hris-org 的 {@code POST /internal/org/approval-callback}
 * （DeptService 幂等更新 ORG_ADJUST 单状态，通过时按 payload 应用结构化变更）。</p>
 */
@FeignClient(name = "hris-org", path = "/internal/org", contextId = "orgApprovalCallback")
public interface OrgApprovalCallbackClient {

    @PostMapping("/approval-callback")
    R<Map<String, Object>> callback(ApprovalCallbackRequest req);
}

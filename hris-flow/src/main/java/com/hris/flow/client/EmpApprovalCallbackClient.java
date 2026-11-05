package com.hris.flow.client;

import com.hris.common.api.R;
import com.hris.flow.dto.ApprovalCallbackRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/**
 * 员工域审批回调客户端（bizType 前缀 EMP_* → hris-employee）。
 *
 * <p>消费 hris-employee 的 {@code POST /internal/emp/approval-callback}
 * （LifecycleCaseService 按单据状态幂等：终态重复回调直接返回成功不重复落账）。</p>
 */
@FeignClient(name = "hris-employee", path = "/internal/emp", contextId = "empApprovalCallback")
public interface EmpApprovalCallbackClient {

    @PostMapping("/approval-callback")
    R<Map<String, Object>> callback(ApprovalCallbackRequest req);
}

package com.hris.flow.client;

import com.hris.common.api.R;
import com.hris.flow.dto.ApprovalCallbackRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/**
 * 知识库域审批回调客户端（bizType 前缀 KBD_* → hris-rag）。
 *
 * <p>消费 hris-rag 的 {@code POST /internal/kb/approval-callback}
 * （KbApprovalCallbackService 按文档版本状态幂等）。</p>
 */
@FeignClient(name = "hris-rag", path = "/internal/kb", contextId = "kbApprovalCallback")
public interface KbApprovalCallbackClient {

    @PostMapping("/approval-callback")
    R<Map<String, Object>> callback(ApprovalCallbackRequest req);
}

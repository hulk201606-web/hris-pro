package com.hris.recruit.controller;

import com.hris.common.api.R;
import com.hris.common.trace.TraceContext;
import com.hris.recruit.dto.ApprovalDtos.ApprovalCallbackRequest;
import com.hris.recruit.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.recruit.service.RecruitApprovalCallbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 招聘域内部端点（网关封禁外部访问，Feign 双向签名）。
 */
@RestController
@RequestMapping("/internal/rcs")
public class RecruitInternalController {

    private final RecruitApprovalCallbackService approvalCallbackService;

    public RecruitInternalController(RecruitApprovalCallbackService approvalCallbackService) {
        this.approvalCallbackService = approvalCallbackService;
    }

    /**
     * 审批结果回调（flow → rcs，docs/19 §9 遗留项四域接入）：按 bizType 幂等迁移单据状态。
     * 内部调用无 Authorization 头，须显式还原租户上下文（租户插件拦截），finally 清理。
     */
    @PostMapping("/approval-callback")
    public R<ApprovalCallbackResponse> approvalCallback(@Valid @RequestBody ApprovalCallbackRequest req) {
        Long tenantId = req.tenantId() != null ? req.tenantId() : TraceContext.getTenantId();
        TraceContext.setTenantId(tenantId);
        try {
            return R.ok(approvalCallbackService.handleCallback(
                    req.bizType(), req.bizKey(), Boolean.TRUE.equals(req.approved())));
        } finally {
            TraceContext.setTenantId(null);
        }
    }
}

package com.hris.recruit.controller;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.recruit.dto.PublicRecruitDtos.PublicApplyRequest;
import com.hris.recruit.dto.PublicRecruitDtos.PublicApplyResponse;
import com.hris.recruit.dto.PublicRecruitDtos.PublicJobItem;
import com.hris.recruit.ratelimit.SimpleIpRateLimiter;
import com.hris.recruit.service.PublicRecruitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 官网免登录公开端点（RCS-F-004，网关白名单 /api/v1/public/** 匿名放行）。
 *
 * <p>租户约定：官网按租户部署，tenantId 由官网站点配置注入为查询参数（必填），服务端据此
 * 还原租户上下文（租户插件按其隔离）。局限：tenantId 为显式参数而非域名推导，伪造可见其他
 * 租户的公开职位（仅公开信息）；投递幂等键含租户，不会跨租户污染数据。</p>
 *
 * <p>限流：POST apply 走内存 IP 计数（60 秒 10 次/IP，MVP 级），超出返回业务码 10429。</p>
 */
@RestController
@RequestMapping("/api/v1/public/rcu")
public class PublicController {

    private final PublicRecruitService publicRecruitService;
    private final SimpleIpRateLimiter rateLimiter;

    public PublicController(PublicRecruitService publicRecruitService, SimpleIpRateLimiter rateLimiter) {
        this.publicRecruitService = publicRecruitService;
        this.rateLimiter = rateLimiter;
    }

    /** 公开职位列表：该租户 OPEN + published=1 的需求单（非敏感字段）。 */
    @GetMapping("/jobs")
    public R<List<PublicJobItem>> jobs(@RequestParam("tenantId") Long tenantId) {
        return withTenant(tenantId, () -> R.ok(publicRecruitService.jobs()));
    }

    /** 官网投递：校验需求 OPEN、幂等、黑名单 fail-open 校验，落 candidate/application/stage_log/resume。 */
    @PostMapping("/jobs/{reqId}/apply")
    public R<PublicApplyResponse> apply(@PathVariable Long reqId,
                                        @RequestParam("tenantId") Long tenantId,
                                        @Valid @RequestBody PublicApplyRequest body,
                                        HttpServletRequest request) {
        if (!rateLimiter.tryAcquire(clientIp(request))) {
            throw new BizException(ErrorCode.RATE_LIMITED, "投递过于频繁，请稍后再试");
        }
        return withTenant(tenantId, () -> R.ok(publicRecruitService.apply(reqId, body)));
    }

    /** 公开端点显式还原租户上下文（租户插件依赖），finally 清理防 ThreadLocal 泄漏。 */
    private <T> R<T> withTenant(Long tenantId, java.util.function.Supplier<R<T>> action) {
        if (tenantId == null || tenantId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "tenantId 必填且须为正整数");
        }
        TraceContext.setTenantId(tenantId);
        try {
            return action.get();
        } finally {
            TraceContext.setTenantId(null);
        }
    }

    /** 客户端 IP：优先 X-Forwarded-For 首段（网关注入），回退 remoteAddr。 */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

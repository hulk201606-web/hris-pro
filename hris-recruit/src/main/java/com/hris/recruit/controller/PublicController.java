package com.hris.recruit.controller;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.recruit.dto.PublicRecruitDtos.PublicApplyRequest;
import com.hris.recruit.dto.PublicRecruitDtos.PublicApplyResponse;
import com.hris.recruit.dto.PublicRecruitDtos.PublicJobItem;
import com.hris.recruit.entity.SiteDomain;
import com.hris.recruit.ratelimit.IpRateLimiter;
import com.hris.recruit.service.PublicRecruitService;
import com.hris.recruit.service.SiteDomainService;
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
 * <p>租户约定（批 7 #13 增强）：优先取 X-Site-Domain 请求头（网关/站点注入，域名透传即可，
 * 网关不改），缺省回退 Host 头（去端口、小写）；域名命中 rcu_site_domain（ENABLED）→
 * 自动定租户并<b>忽略显式 tenantId 参数</b>；未命中回退显式 tenantId 参数保持兼容。
 * 局限（如实注明）：未命中/未配置域名映射时 tenantId 仍为显式参数，伪造 tenantId 可见该
 * 租户公开职位（仅公开信息，不构成越权；投递幂等键含租户，不会跨租户污染数据）。</p>
 *
 * <p>限流：POST apply 走 Redis 固定窗口 IP 计数（60 秒 10 次/IP，多实例共享，批 7 #12），
 * Redis 不可用时 fail-open 放行，超出返回业务码 10429。</p>
 */
@RestController
@RequestMapping("/api/v1/public/rcu")
public class PublicController {

    /** 站点域名请求头（优先于 Host 头）。 */
    public static final String HEADER_SITE_DOMAIN = "X-Site-Domain";

    private final PublicRecruitService publicRecruitService;
    private final IpRateLimiter rateLimiter;
    private final SiteDomainService siteDomainService;

    public PublicController(PublicRecruitService publicRecruitService, IpRateLimiter rateLimiter,
                            SiteDomainService siteDomainService) {
        this.publicRecruitService = publicRecruitService;
        this.rateLimiter = rateLimiter;
        this.siteDomainService = siteDomainService;
    }

    /** 公开职位列表：该租户 OPEN + published=1 的需求单（非敏感字段）。 */
    @GetMapping("/jobs")
    public R<List<PublicJobItem>> jobs(@RequestParam(value = "tenantId", required = false) Long tenantId,
                                       HttpServletRequest request) {
        return withTenant(request, tenantId, () -> R.ok(publicRecruitService.jobs()));
    }

    /** 官网投递：校验需求 OPEN、幂等、黑名单校验（降级模式配置化），落四件套。 */
    @PostMapping("/jobs/{reqId}/apply")
    public R<PublicApplyResponse> apply(@PathVariable Long reqId,
                                        @RequestParam(value = "tenantId", required = false) Long tenantId,
                                        @Valid @RequestBody PublicApplyRequest body,
                                        HttpServletRequest request) {
        if (!rateLimiter.tryAcquire(clientIp(request))) {
            throw new BizException(ErrorCode.RATE_LIMITED, "投递过于频繁，请稍后再试");
        }
        return withTenant(request, tenantId, () -> R.ok(publicRecruitService.apply(reqId, body)));
    }

    /**
     * 租户解析：X-Site-Domain（回退 Host 去端口）命中启用映射 → 用映射租户并忽略显式参数；
     * 未命中 → 回退显式 tenantId（保持兼容）。 finally 清理防 ThreadLocal 泄漏。
     */
    private <T> R<T> withTenant(HttpServletRequest request, Long tenantIdParam,
                                java.util.function.Supplier<R<T>> action) {
        Long tenantId = resolveTenant(request, tenantIdParam);
        if (tenantId == null || tenantId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "tenantId 必填且须为正整数（或通过 X-Site-Domain/Host 提供已映射域名）");
        }
        TraceContext.setTenantId(tenantId);
        try {
            return action.get();
        } finally {
            TraceContext.setTenantId(null);
        }
    }

    /** 域名命中映射定租户；未命中回退显式参数。 */
    private Long resolveTenant(HttpServletRequest request, Long tenantIdParam) {
        String domain = request.getHeader(HEADER_SITE_DOMAIN);
        if (domain == null || domain.isBlank()) {
            String host = request.getHeader("Host");
            if (host != null && !host.isBlank()) {
                domain = host.split(":")[0];
            }
        }
        if (domain != null && !domain.isBlank()) {
            SiteDomain hit = siteDomainService.findEnabledByDomain(domain.strip().toLowerCase());
            if (hit != null) {
                // 命中映射：忽略显式 tenantId 参数（防伪造）
                return hit.getTenantId();
            }
        }
        return tenantIdParam;
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

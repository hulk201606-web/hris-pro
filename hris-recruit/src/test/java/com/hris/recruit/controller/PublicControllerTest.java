package com.hris.recruit.controller;

import com.hris.common.exception.BizException;
import com.hris.common.trace.TraceContext;
import com.hris.recruit.entity.SiteDomain;
import com.hris.recruit.ratelimit.IpRateLimiter;
import com.hris.recruit.service.PublicRecruitService;
import com.hris.recruit.service.SiteDomainService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * PublicController 单元测试（批 7 #13）：X-Site-Domain/Host 命中映射自动定租户并忽略
 * 显式参数 / 未命中回退显式参数 / 两源皆缺 PARAM_INVALID。
 * 租户上下文正确性以 service 调用瞬间 TraceContext 快照为准（withTenant 在 finally 清理）。
 */
@ExtendWith(MockitoExtension.class)
class PublicControllerTest {

    private static final Long MAPPED_TENANT = 100001L;
    private static final Long PARAM_TENANT = 200002L;

    @Mock
    private PublicRecruitService publicRecruitService;
    @Mock
    private IpRateLimiter rateLimiter;
    @Mock
    private SiteDomainService siteDomainService;

    private PublicController controller;
    private MockHttpServletRequest request;
    /** jobs() 调用瞬间的租户上下文快照（withTenant set 之后、finally 清理之前）。 */
    private List<Long> tenantSnapshots;

    @BeforeEach
    void setUp() {
        controller = new PublicController(publicRecruitService, rateLimiter, siteDomainService);
        request = new MockHttpServletRequest();
        tenantSnapshots = new ArrayList<>();
        lenient().when(rateLimiter.tryAcquire(any())).thenReturn(true);
        lenient().when(publicRecruitService.jobs()).thenAnswer(inv -> {
            tenantSnapshots.add(TraceContext.getTenantId());
            return List.of();
        });
    }

    @AfterEach
    void tearDown() {
        TraceContext.setTenantId(null);
    }

    private SiteDomain mapping(Long tenantId) {
        SiteDomain s = new SiteDomain();
        s.setTenantId(tenantId);
        s.setDomain("careers.acme.com");
        s.setStatus("ENABLED");
        return s;
    }

    private Long tenantSeenByService() {
        assertEquals(1, tenantSnapshots.size());
        assertNull(TraceContext.getTenantId(), "请求结束上下文应被 finally 清理");
        return tenantSnapshots.get(0);
    }

    @Test
    @DisplayName("X-Site-Domain 命中映射 → 用映射租户，忽略显式 tenantId 参数（防伪造）")
    void domainHeaderHitOverridesParam() {
        request.addHeader(PublicController.HEADER_SITE_DOMAIN, "careers.acme.com");
        when(siteDomainService.findEnabledByDomain("careers.acme.com")).thenReturn(mapping(MAPPED_TENANT));

        controller.jobs(999999L, request);

        assertEquals(MAPPED_TENANT, tenantSeenByService());
    }

    @Test
    @DisplayName("无 X-Site-Domain 时回退 Host 头（去端口、小写），命中映射定租户")
    void hostHeaderHitResolvesTenant() {
        request.addHeader("Host", "Careers.Acme.com:8080");
        when(siteDomainService.findEnabledByDomain("careers.acme.com")).thenReturn(mapping(MAPPED_TENANT));

        controller.jobs(null, request);

        assertEquals(MAPPED_TENANT, tenantSeenByService());
    }

    @Test
    @DisplayName("域名未命中 → 回退显式 tenantId 参数（兼容语义）")
    void domainMissFallsBackToParam() {
        request.addHeader(PublicController.HEADER_SITE_DOMAIN, "unknown.com");
        when(siteDomainService.findEnabledByDomain("unknown.com")).thenReturn(null);

        controller.jobs(PARAM_TENANT, request);

        assertEquals(PARAM_TENANT, tenantSeenByService());
    }

    @Test
    @DisplayName("无域名头且未传 tenantId → PARAM_INVALID")
    void missingBothSourcesRejected() {
        BizException e = assertThrows(BizException.class, () -> controller.jobs(null, request));
        assertEquals(10001, e.getCode());
    }

    @Test
    @DisplayName("域名头优先于 Host 头：两者同时存在且 Host 未映射时仍以域名头为准")
    void siteDomainHeaderWinsOverHost() {
        request.addHeader(PublicController.HEADER_SITE_DOMAIN, "careers.acme.com");
        request.addHeader("Host", "other.example.com");
        when(siteDomainService.findEnabledByDomain("careers.acme.com")).thenReturn(mapping(MAPPED_TENANT));

        controller.jobs(null, request);

        assertEquals(MAPPED_TENANT, tenantSeenByService());
    }
}

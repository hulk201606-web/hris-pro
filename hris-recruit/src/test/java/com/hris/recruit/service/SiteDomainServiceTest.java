package com.hris.recruit.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.dto.SiteDomainDtos.SiteDomainCreateRequest;
import com.hris.recruit.dto.SiteDomainDtos.SiteDomainResponse;
import com.hris.recruit.dto.SiteDomainDtos.SiteDomainUpdateRequest;
import com.hris.recruit.entity.SiteDomain;
import com.hris.recruit.mapper.SiteDomainMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SiteDomainService 单元测试（批 7 #13）：域名归一化 / uk 冲突转业务错误 /
 * findEnabledByDomain 跨租户查询透传 / 更新与删除语义。
 */
@ExtendWith(MockitoExtension.class)
class SiteDomainServiceTest {

    private static final Long ID = 3001L;

    @Mock
    private SiteDomainMapper mapper;

    private SiteDomainService service;

    @BeforeEach
    void setUp() {
        service = new SiteDomainService(mapper);
    }

    private SiteDomain entity() {
        SiteDomain e = new SiteDomain();
        e.setId(ID);
        e.setTenantId(100001L);
        e.setDomain("careers.acme.com");
        e.setStatus(SiteDomainService.STATUS_ENABLED);
        return e;
    }

    @Test
    @DisplayName("create：域名归一化为小写去空白，缺省 status=ENABLED")
    void createNormalizesDomain() {
        SiteDomainResponse resp = service.create(new SiteDomainCreateRequest("  Careers.ACME.com ", null));

        assertEquals("careers.acme.com", resp.domain());
        assertEquals(SiteDomainService.STATUS_ENABLED, resp.status());
        verify(mapper).insert(any(SiteDomain.class));
    }

    @Test
    @DisplayName("create：uk(domain) 冲突（含跨租户）→ PARAM_INVALID 域名已被映射")
    void createDuplicateDomainRejected() {
        when(mapper.insert(any(SiteDomain.class))).thenThrow(new DuplicateKeyException("uk_rcu_site_domain"));

        BizException e = assertThrows(BizException.class,
                () -> service.create(new SiteDomainCreateRequest("careers.acme.com", null)));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
    }

    @Test
    @DisplayName("create：域名携带协议/端口/路径 → PARAM_INVALID")
    void createRejectsProtocolOrPort() {
        assertThrows(BizException.class,
                () -> service.create(new SiteDomainCreateRequest("https://a.com", null)));
        assertThrows(BizException.class,
                () -> service.create(new SiteDomainCreateRequest("a.com:8080", null)));
    }

    @Test
    @DisplayName("create：status 非法 → PARAM_INVALID")
    void createRejectsBadStatus() {
        BizException e = assertThrows(BizException.class,
                () -> service.create(new SiteDomainCreateRequest("a.com", "PAUSED")));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
    }

    @Test
    @DisplayName("update：可改域名与状态；不存在 → NOT_FOUND")
    void updateSemantics() {
        when(mapper.selectById(ID)).thenReturn(entity());
        SiteDomainResponse resp = service.update(ID, new SiteDomainUpdateRequest("Jobs.ACME.com", "DISABLED"));
        assertEquals("jobs.acme.com", resp.domain());
        assertEquals(SiteDomainService.STATUS_DISABLED, resp.status());

        when(mapper.selectById(404L)).thenReturn(null);
        BizException e = assertThrows(BizException.class,
                () -> service.update(404L, new SiteDomainUpdateRequest(null, null)));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    @DisplayName("delete：行不存在 → NOT_FOUND")
    void deleteNotFound() {
        when(mapper.deleteById(404L)).thenReturn(0);
        BizException e = assertThrows(BizException.class, () -> service.delete(404L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    @DisplayName("findEnabledByDomain：透传 mapper（跨租户查询由 @InterceptorIgnore 保证）")
    void findEnabledDelegates() {
        when(mapper.findEnabledByDomain("careers.acme.com")).thenReturn(entity());
        assertEquals(ID, service.findEnabledByDomain("careers.acme.com").getId());
        when(mapper.findEnabledByDomain("miss.com")).thenReturn(null);
        assertEquals(null, service.findEnabledByDomain("miss.com"));
    }
}

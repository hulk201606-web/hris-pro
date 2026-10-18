package com.hris.platform.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.platform.entity.SysAuditLog;
import com.hris.platform.mapper.SysAuditLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuditLogService 单元测试（PLT-F-008 审计只追加+ 租户内查询，docs/08 §5）。
 *
 * <p>核心是<b>多租户隔离边界</b>：query 必须显式按当前 TraceContext 的 tenantId 过滤，
 * 缺租户上下文时抛 FORBIDDEN 而非放行全量。若该守卫失效，审计查询会跨租户泄露
 * 他人的操作记录（谁改了哪条薪酬/档案数据）。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuditLogTenantGuardTest {

    @Mock
    private SysAuditLogMapper mapper;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(mapper);
    }

    @AfterEach
    void clearTraceContext() {
        TraceContext.clear();
    }

    @Nested
    @DisplayName("租户隔离守卫")
    class TenantGuard {

        @Test
        @DisplayName("缺少租户上下文 → FORBIDDEN，不返回任何数据")
        void rejectsMissingTenantContext() {
            TraceContext.setTenantId(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.query(null, null, null, null, 1, 10));

            assertEquals(ErrorCode.FORBIDDEN.getCode(), e.getCode());
        }

        @Test
        @DisplayName("未调用 setTenantId（ThreadLocal 空）→ 同样 FORBIDDEN")
        void rejectsAbsentTenantId() {
            assertThrows(BizException.class,
                    () -> service.query(null, null, null, null, 1, 10));
        }

        @Test
        @DisplayName("有租户上下文 → 正常查询（不再抛 FORBIDDEN）")
        void queriesWithTenantContext() {
            TraceContext.setTenantId(1L);
            when(mapper.selectPage(any(), any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());

            assertEquals(0, service.query(null, null, null, null, 1, 10).total());
        }
    }

    @Nested
    @DisplayName("append：只追加写入")
    class Append {

        @Test
        @DisplayName("显式传tenantId → 按传入值落库（全局流可跨租户由调用方指定）")
        void appendsWithExplicitTenant() {
            service.append(9L, 100L, "UPDATE", "employee", Map.of("field", "salary"), "10.0.0.1");

            ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
            verify(mapper).insert(captor.capture());
            assertEquals(9L, captor.getValue().getTenantId());
            assertEquals(100L, captor.getValue().getActorId());
            assertEquals("UPDATE", captor.getValue().getAction());
            assertEquals("employee", captor.getValue().getResource());
            assertEquals("10.0.0.1", captor.getValue().getIp());
        }

        @Test
        @DisplayName("从上下文取 tenant/user 的重载 → 落当前请求身份")
        void appendsFromTraceContext() {
            TraceContext.setTenantId(7L);
            TraceContext.setUserId(200L);

            service.append("DELETE", "contract", Map.of(), "10.0.0.2");

            ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
            verify(mapper).insert(captor.capture());
            assertEquals(7L, captor.getValue().getTenantId());
            assertEquals(200L, captor.getValue().getActorId());
        }

        @Test
        @DisplayName("detail 序列化为 JSON 字符串（非 map 直存）")
        void serializesDetailAsJson() {
            service.append(1L, 100L, "UPDATE", "employee", Map.of("field", "salary"), null);

            ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
            verify(mapper).insert(captor.capture());
            org.junit.jupiter.api.Assertions.assertTrue(
                    captor.getValue().getDetail().contains("salary"),
                    "detail 应序列化为 JSON，实际=" + captor.getValue().getDetail());
        }

        @Test
        @DisplayName("createdAt 由服务填充，不依赖调用方传入")
        void fillsCreatedAt() {
            service.append(1L, 100L, "LOGIN", "auth", Map.of(), null);

            ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
            verify(mapper).insert(captor.capture());
            org.junit.jupiter.api.Assertions.assertNotNull(captor.getValue().getCreatedAt());
        }
    }
}
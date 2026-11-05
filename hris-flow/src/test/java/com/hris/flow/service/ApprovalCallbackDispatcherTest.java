package com.hris.flow.service;

import com.hris.common.trace.TraceContext;
import com.hris.flow.client.AttApprovalCallbackClient;
import com.hris.flow.client.EmpApprovalCallbackClient;
import com.hris.flow.client.OrgApprovalCallbackClient;
import com.hris.flow.dto.ApprovalCallbackRequest;
import com.hris.flow.entity.EventOutbox;
import com.hris.flow.enums.OutboxState;
import com.hris.flow.mapper.EventOutboxMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ApprovalCallbackDispatcher 单元测试（docs/19 §8.3-1 审批回调闭环）：
 * 前缀路由 + 失败落 outbox（审批动作永不因回调失败而失败）。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalCallbackDispatcherTest {

    @Mock
    private AttApprovalCallbackClient attClient;
    @Mock
    private EmpApprovalCallbackClient empClient;
    @Mock
    private OrgApprovalCallbackClient orgClient;
    @Mock
    private EventOutboxMapper outboxMapper;

    private ApprovalCallbackDispatcher dispatcher;

    @AfterEach
    void clearTraceContext() {
        TraceContext.clear();
    }

    private ApprovalCallbackDispatcher dispatcher() {
        return new ApprovalCallbackDispatcher(attClient, empClient, orgClient, outboxMapper);
    }

    @Nested
    @DisplayName("前缀路由：ATT_/EMP_/ORG_ 走对应域 client，其它不路由")
    class Routing {

        @Test
        @DisplayName("ATT_ 前缀 → 考勤域 client，approved 透传")
        void routesAttToAttendance() {
            dispatcher = dispatcher();

            boolean routed = dispatcher.deliver("ATT_LEAVE", "att_leave_1", 9L,
                    true, "APPROVED", "ok", "PI-1");

            assertTrue(routed);
            ArgumentCaptor<ApprovalCallbackRequest> captor =
                    ArgumentCaptor.forClass(ApprovalCallbackRequest.class);
            verify(attClient).callback(captor.capture());
            assertEquals("ATT_LEAVE", captor.getValue().bizType());
            assertEquals("att_leave_1", captor.getValue().bizKey());
            assertEquals(Boolean.TRUE, captor.getValue().approved());
            verify(outboxMapper, never()).insert(any(EventOutbox.class));
        }

        @Test
        @DisplayName("EMP_ 前缀 → 员工域 client")
        void routesEmpToEmployee() {
            dispatcher = dispatcher();

            assertTrue(dispatcher.deliver("EMP_ONBOARD", "emp_onboard_1", 9L,
                    true, "APPROVED", null, "PI-2"));

            verify(empClient).callback(any(ApprovalCallbackRequest.class));
            verify(attClient, never()).callback(any());
            verify(orgClient, never()).callback(any());
        }

        @Test
        @DisplayName("ORG_ 前缀 → 组织域 client")
        void routesOrgToOrg() {
            dispatcher = dispatcher();

            assertTrue(dispatcher.deliver("ORG_ADJUST", "ORG_ADJUST:123", 9L,
                    false, "REJECTED", "不同意", "PI-3"));

            verify(orgClient).callback(any(ApprovalCallbackRequest.class));
        }

        @Test
        @DisplayName("RCS_/CMP_/PRF_ 等无回调端点 → 不路由，返回 false")
        void skipsUnroutedPrefix() {
            dispatcher = dispatcher();

            assertFalse(dispatcher.deliver("RCS_OFFER", "rcs_offer_1", 9L,
                    true, "APPROVED", null, "PI-4"));

            verify(attClient, never()).callback(any());
            verify(empClient, never()).callback(any());
            verify(orgClient, never()).callback(any());
        }

        @Test
        @DisplayName("bizType 为空 → 不路由")
        void skipsNullBizType() {
            dispatcher = dispatcher();

            assertFalse(dispatcher.deliver(null, "k", 9L, true, "APPROVED", null, null));
        }
    }

    @Nested
    @DisplayName("失败兜底：Feign 异常/无路由 → 落 outbox PENDING，不向上抛")
    class Fallback {

        @Test
        @DisplayName("Feign 调用失败 → 落 outbox PENDING 且不抛异常（审批动作不受影响）")
        void savesPendingWhenFeignFails() {
            dispatcher = dispatcher();
            when(attClient.callback(any())).thenThrow(new RuntimeException("connection refused"));

            assertDoesNotThrow(() -> dispatcher.dispatchOnTerminal("ATT_LEAVE", "att_leave_1",
                    9L, true, "APPROVED", "ok", "PI-1"));

            ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
            verify(outboxMapper).insert(captor.capture());
            assertEquals(OutboxState.PENDING.name(), captor.getValue().getState());
            assertEquals(ApprovalCallbackDispatcher.TOPIC_APPROVAL_CALLBACK, captor.getValue().getTopic());
            assertTrue(captor.getValue().getPayload().contains("ATT_LEAVE"));
            assertTrue(captor.getValue().getPayload().contains("att_leave_1"));
        }

        @Test
        @DisplayName("无路由 bizType → 事件落 outbox PENDING（供 MQ 后续消费），不报错")
        void savesPendingWhenUnrouted() {
            dispatcher = dispatcher();

            dispatcher.dispatchOnTerminal("RCS_OFFER", "rcs_offer_1", 9L,
                    true, "APPROVED", null, "PI-4");

            ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
            verify(outboxMapper).insert(captor.capture());
            assertEquals(OutboxState.PENDING.name(), captor.getValue().getState());
        }

        @Test
        @DisplayName("投递成功 → 不落 outbox")
        void noOutboxWhenDelivered() {
            dispatcher = dispatcher();

            dispatcher.dispatchOnTerminal("EMP_REGULAR", "emp_regular_1", 9L,
                    true, "APPROVED", null, "PI-5");

            verify(outboxMapper, never()).insert(any(EventOutbox.class));
        }

        @Test
        @DisplayName("落 outbox 本身失败 → 双重兜底，仍不向上抛")
        void swallowsOutboxFailure() {
            dispatcher = dispatcher();
            when(attClient.callback(any())).thenThrow(new RuntimeException("down"));
            when(outboxMapper.insert(any(EventOutbox.class)))
                    .thenThrow(new RuntimeException("db down"));

            assertDoesNotThrow(() -> dispatcher.dispatchOnTerminal("ATT_LEAVE", "att_leave_1",
                    9L, true, "APPROVED", null, "PI-1"));
        }
    }

    @Nested
    @DisplayName("租户上下文：按事件值临时还原，结束恢复原值")
    class TenantContext {

        @Test
        @DisplayName("重试线程无租户上下文 → 调用期还原事件租户，finally 恢复原值")
        void restoresTenantAfterCall() {
            dispatcher = dispatcher();
            TraceContext.setTenantId(77L);

            dispatcher.dispatchOnTerminal("ORG_ADJUST", "ORG_ADJUST:9", 9L,
                    true, "APPROVED", null, "PI-6");

            assertEquals(77L, TraceContext.getTenantId(), "调用后应恢复调用线程原租户");
            verify(orgClient).callback(any(ApprovalCallbackRequest.class));
        }
    }
}

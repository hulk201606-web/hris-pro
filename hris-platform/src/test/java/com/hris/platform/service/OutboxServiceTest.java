package com.hris.platform.service;

import com.hris.platform.entity.SysOutboxMessage;
import com.hris.platform.mapper.SysOutboxMessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OutboxService 单元测试 —— 全局事件表（发件箱模式）。
 *
 * <p>覆盖入队默认状态（PENDING + retryCnt=0 + payload 序列化）、投递/死信状态机
 * 的空安全（记录不存在时静默跳过）。listPending 仅含 limit 钳制（Math.max(1,limit)）与条件组装，
 * 属 CRUD 透传，未写测试。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private SysOutboxMessageMapper mapper;

    private OutboxService service;

    @BeforeEach
    void setUp() {
        service = new OutboxService(mapper);
    }

    @Nested
    @DisplayName("enqueue：入队事件")
    class Enqueue {

        @Test
        @DisplayName("入队 → 状态 PENDING、retryCnt=0、payload 序列化为 JSON")
        void enqueuesAsPending() {
            SysOutboxMessage m = service.enqueue("evt-1", "EMP_CREATED", Map.of("empId", 1));

            verify(mapper).insert(m);
            assertEquals("PENDING", m.getState());
            assertEquals(0, m.getRetryCnt());
            assertEquals("evt-1", m.getEventId());
            assertEquals("EMP_CREATED", m.getTopic());
            assertNotNull(m.getPayload());
            assertTrue(m.getPayload().contains("empId"), "payload 应序列化为 JSON 字符串");
        }
    }

    @Nested
    @DisplayName("markSent / markDead：投递状态机（空安全）")
    class StateMachine {

        @Test
        @DisplayName("记录不存在 → markSent 静默跳过，不更新")
        void markSentSkipsMissing() {
            when(mapper.selectById(404L)).thenReturn(null);

            service.markSent(404L);

            verify(mapper, never()).updateById(any(SysOutboxMessage.class));
        }

        @Test
        @DisplayName("markSent → 置 SENT")
        void marksSent() {
            SysOutboxMessage m = new SysOutboxMessage();
            m.setId(1L);
            m.setState("PENDING");
            when(mapper.selectById(1L)).thenReturn(m);

            service.markSent(1L);

            assertEquals("SENT", m.getState());
            verify(mapper).updateById(m);
        }

        @Test
        @DisplayName("markDead → 置 DEAD")
        void marksDead() {
            SysOutboxMessage m = new SysOutboxMessage();
            m.setId(1L);
            m.setState("PENDING");
            when(mapper.selectById(1L)).thenReturn(m);

            service.markDead(1L);

            assertEquals("DEAD", m.getState());
        }
    }

}

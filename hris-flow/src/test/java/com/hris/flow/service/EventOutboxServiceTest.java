package com.hris.flow.service;

import com.hris.flow.entity.EventOutbox;
import com.hris.flow.enums.OutboxState;
import com.hris.flow.mapper.EventOutboxMapper;
import com.hris.flow.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EventOutboxService.dispatchPending 单元测试（docs/19 §8.7-1；批 12 #25 事件总线）：
 * 审批回调重试、MQ 发送成功置 SENT / 异常保留 PENDING / 三次后 DEAD / mq-disabled 兼容。
 */
@ExtendWith(MockitoExtension.class)
class EventOutboxServiceTest {

    @Mock
    private EventOutboxMapper mapper;
    @Mock
    private ApprovalCallbackDispatcher callbackDispatcher;

    private EventOutboxService service;

    /** 缺省 mq-disabled（旧行为）：仅落表不发送。 */
    @BeforeEach
    void setUp() {
        service = new EventOutboxService(mapper, callbackDispatcher,
                new OutboxMqPublisher(false, "hris-flow-events", 3, (t, k, b) -> {
                }));
    }

    private EventOutboxService serviceWithMq(boolean enabled, OutboxMqSender sender) {
        return new EventOutboxService(mapper, callbackDispatcher,
                new OutboxMqPublisher(enabled, "hris-flow-events", 3, sender));
    }

    private EventOutbox pendingRow(String topic, Map<String, Object> payload) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", "EVT-1");
        envelope.put("topic", topic);
        envelope.put("tenantId", 9L);
        envelope.put("bizKey", payload.get("bizKey"));
        envelope.put("payload", payload);
        EventOutbox row = new EventOutbox();
        row.setId(1L);
        row.setEventId("EVT-1");
        row.setTopic(topic);
        row.setPayload(JsonUtil.toJson(envelope));
        row.setState(OutboxState.PENDING.name());
        row.setRetryCnt(0);
        return row;
    }

    private Map<String, Object> callbackPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bizType", "ATT_LEAVE");
        payload.put("bizKey", "att_leave_1");
        payload.put("approved", true);
        payload.put("outcome", "APPROVED");
        return payload;
    }

    @Nested
    @DisplayName("审批回调事件重试")
    class CallbackRetry {

        @Test
        @DisplayName("投递成功 → 置 SENT，更新落库")
        void marksSentOnSuccess() {
            EventOutbox row = pendingRow(ApprovalCallbackDispatcher.TOPIC_APPROVAL_CALLBACK,
                    callbackPayload());
            when(mapper.selectList(any())).thenReturn(List.of(row));
            when(callbackDispatcher.deliver("ATT_LEAVE", "att_leave_1", 9L, true,
                    "APPROVED", null, null)).thenReturn(true);

            Map<String, Integer> stats = service.dispatchPending(100);

            assertEquals(OutboxState.SENT.name(), row.getState());
            verify(mapper).updateById(row);
            assertEquals(1, stats.get("sent"));
            assertEquals(0, stats.get("failed"));
        }

        @Test
        @DisplayName("投递失败 → retryCnt+1 保留 PENDING 供下次重试")
        void keepsPendingOnFailure() {
            EventOutbox row = pendingRow(ApprovalCallbackDispatcher.TOPIC_APPROVAL_CALLBACK,
                    callbackPayload());
            when(mapper.selectList(any())).thenReturn(List.of(row));
            when(callbackDispatcher.deliver(any(), any(), any(), anyBoolean(), any(), any(), any()))
                    .thenReturn(false);

            service.dispatchPending(100);

            assertEquals(OutboxState.PENDING.name(), row.getState(), "失败不得误标 SENT");
            assertEquals(1, row.getRetryCnt());
            verify(mapper).updateById(row);
        }

        @Test
        @DisplayName("投递抛异常 → 按失败处理，保留 PENDING")
        void keepsPendingOnException() {
            EventOutbox row = pendingRow(ApprovalCallbackDispatcher.TOPIC_APPROVAL_CALLBACK,
                    callbackPayload());
            when(mapper.selectList(any())).thenReturn(List.of(row));
            when(callbackDispatcher.deliver(any(), any(), any(), anyBoolean(), any(), any(), any()))
                    .thenThrow(new RuntimeException("boom"));

            Map<String, Integer> stats = service.dispatchPending(100);

            assertEquals(OutboxState.PENDING.name(), row.getState());
            assertEquals(1, stats.get("failed"));
        }

        @Test
        @DisplayName("payload 结构异常（缺 payload）→ 按失败处理不抛出")
        void treatsBrokenPayloadAsFailure() {
            EventOutbox row = pendingRow(ApprovalCallbackDispatcher.TOPIC_APPROVAL_CALLBACK,
                    callbackPayload());
            row.setPayload(JsonUtil.toJson(Map.of("eventId", "EVT-1", "topic",
                    ApprovalCallbackDispatcher.TOPIC_APPROVAL_CALLBACK)));
            when(mapper.selectList(any())).thenReturn(List.of(row));

            Map<String, Integer> stats = service.dispatchPending(100);

            assertEquals(1, stats.get("failed"));
            verify(mapper).updateById(row);
        }
    }

    @Nested
    @DisplayName("MQ 事件投递（mq-enabled=true）")
    class MqEventsPublish {

        private EventOutbox mqRow(int retryCnt) {
            EventOutbox row = pendingRow(EventOutboxService.TOPIC_INSTANCE_STARTED,
                    Map.of("bizType", "ATT_LEAVE", "bizKey", "att_leave_1", "bizState", "RUNNING"));
            row.setRetryCnt(retryCnt);
            return row;
        }

        @Test
        @DisplayName("发送成功 → 置 SENT，消息体含 eventId/type/bizKey/payload/tenantId")
        void marksSentOnPublishSuccess() {
            EventOutbox row = mqRow(0);
            when(mapper.selectList(any())).thenReturn(List.of(row));
            List<String> sentBodies = new java.util.ArrayList<>();
            EventOutboxService mqService = serviceWithMq(true, (topic, key, body) -> {
                assertEquals("hris-flow-events", topic);
                assertEquals("EVT-1", key);
                sentBodies.add(body);
            });

            Map<String, Integer> stats = mqService.dispatchPending(100);

            assertEquals(OutboxState.SENT.name(), row.getState());
            assertEquals(1, stats.get("sent"));
            assertEquals(1, sentBodies.size());
            assertTrue(sentBodies.get(0).contains("\"eventId\":\"EVT-1\""));
            assertTrue(sentBodies.get(0).contains(EventOutboxService.TOPIC_INSTANCE_STARTED));
            assertTrue(sentBodies.get(0).contains("\"tenantId\":9"));
            verify(mapper).updateById(row);
        }

        @Test
        @DisplayName("发送异常 → retryCnt+1 保留 PENDING（fail-open 不阻断审批）")
        void keepsPendingOnPublishFailure() {
            EventOutbox row = mqRow(0);
            when(mapper.selectList(any())).thenReturn(List.of(row));
            EventOutboxService mqService = serviceWithMq(true, (t, k, b) -> {
                throw new RuntimeException("broker unavailable");
            });

            Map<String, Integer> stats = mqService.dispatchPending(100);

            assertEquals(OutboxState.PENDING.name(), row.getState());
            assertEquals(1, row.getRetryCnt());
            assertEquals(1, stats.get("failed"));
            assertEquals(0, stats.get("dead"));
        }

        @Test
        @DisplayName("第三次仍失败 → 置 DEAD 并计入 dead 统计")
        void movesToDeadAfterMaxRetry() {
            EventOutbox row = mqRow(2);
            when(mapper.selectList(any())).thenReturn(List.of(row));
            EventOutboxService mqService = serviceWithMq(true, (t, k, b) -> {
                throw new IllegalStateException("send timeout");
            });

            Map<String, Integer> stats = mqService.dispatchPending(100);

            assertEquals(OutboxState.DEAD.name(), row.getState());
            assertEquals(3, row.getRetryCnt());
            assertEquals(1, stats.get("dead"));
            assertEquals(0, stats.get("failed"));
        }
    }

    @Nested
    @DisplayName("MQ 事件：mq-disabled 时保持旧行为（仅落表，跳过不误标）")
    class MqEventsSkipped {

        @Test
        @DisplayName("INSTANCE_STARTED / INSTANCE_FINISHED → 不投递不改状态")
        void skipsMqTopics() {
            EventOutbox started = pendingRow(EventOutboxService.TOPIC_INSTANCE_STARTED,
                    Map.of("bizType", "ATT_LEAVE", "bizKey", "att_leave_1"));
            EventOutbox finished = pendingRow(EventOutboxService.TOPIC_INSTANCE_FINISHED,
                    Map.of("bizType", "EMP_ONBOARD", "bizKey", "emp_onboard_1"));
            when(mapper.selectList(any())).thenReturn(List.of(started, finished));

            Map<String, Integer> stats = service.dispatchPending(100);

            assertEquals(2, stats.get("skipped"));
            assertEquals(0, stats.get("sent"));
            assertEquals(OutboxState.PENDING.name(), started.getState());
            verify(callbackDispatcher, never()).deliver(any(), any(), any(), anyBoolean(), any(), any(), any());
            verify(mapper, never()).updateById(any(EventOutbox.class));
            assertEquals(0, stats.get("dead"));
        }
    }
}

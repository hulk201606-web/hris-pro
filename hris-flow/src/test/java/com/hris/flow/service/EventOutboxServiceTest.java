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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EventOutboxService.dispatchPending 单元测试（docs/19 §8.7-1 最小重试）：
 * 审批回调事件重试成功置 SENT / 失败保留 PENDING；MQ 事件跳过不误标。
 */
@ExtendWith(MockitoExtension.class)
class EventOutboxServiceTest {

    @Mock
    private EventOutboxMapper mapper;
    @Mock
    private ApprovalCallbackDispatcher callbackDispatcher;

    private EventOutboxService service;

    @BeforeEach
    void setUp() {
        service = new EventOutboxService(mapper, callbackDispatcher);
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
    @DisplayName("MQ 事件：RocketMQ 未接入，跳过不误标")
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
        }
    }
}

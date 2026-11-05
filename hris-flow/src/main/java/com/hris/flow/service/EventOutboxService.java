package com.hris.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.trace.TraceContext;
import com.hris.flow.entity.EventOutbox;
import com.hris.flow.enums.OutboxState;
import com.hris.flow.mapper.EventOutboxMapper;
import com.hris.flow.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 事件出站服务（FLW-F-012）。本地事务内写 flow_ext_event_outbox。
 *
 * <p>两类事件：① MQ 事件（INSTANCE_STARTED/TASK_COMPLETED/INSTANCE_FINISHED），RocketMQ
 * producer 未接入，保持 PENDING 不误标；② 审批回调事件（{@link ApprovalCallbackDispatcher}
 * Feign 失败落表），由 {@link #dispatchPending(int)} 走同一 dispatcher 重试，成功置 SENT。</p>
 */
@Service
public class EventOutboxService {

    private static final Logger log = LoggerFactory.getLogger(EventOutboxService.class);

    /** 实例启动事件 topic（docs/07 §4：hris-{域}-{事件过去式}） */
    public static final String TOPIC_INSTANCE_STARTED = "hris-flow-instance-started";
    public static final String TOPIC_TASK_COMPLETED = "hris-flow-task-completed";
    public static final String TOPIC_INSTANCE_FINISHED = "hris-flow-instance-finished";

    private final EventOutboxMapper mapper;
    private final ApprovalCallbackDispatcher callbackDispatcher;

    public EventOutboxService(EventOutboxMapper mapper, ApprovalCallbackDispatcher callbackDispatcher) {
        this.mapper = mapper;
        this.callbackDispatcher = callbackDispatcher;
    }

    /**
     * 发布事件（本地事务内落表）。event_id 全局唯一（平台级）。
     */
    public void publish(String topic, Long tenantId, String bizKey, Map<String, Object> payload) {
        Map<String, Object> envelope = new HashMap<>();
        String eventId = UUID.randomUUID().toString().replace("-", "");
        envelope.put("eventId", eventId);
        envelope.put("topic", topic);
        envelope.put("tenantId", tenantId);
        envelope.put("traceId", TraceContext.getTraceId());
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("bizKey", bizKey);
        envelope.put("payload", payload == null ? Map.of() : payload);

        EventOutbox row = new EventOutbox();
        row.setEventId(eventId);
        row.setTopic(topic);
        row.setPayload(JsonUtil.toJson(envelope));
        row.setState(OutboxState.PENDING.name());
        row.setRetryCnt(0);
        mapper.insert(row);
    }

    /**
     * 重试待发事件（docs/19 §8.7-1）：遍历 PENDING 事件，审批回调事件按 bizType 前缀走
     * {@link ApprovalCallbackDispatcher#deliver} 重试，成功置 SENT、失败 retryCnt+1 保留 PENDING；
     * MQ 事件（其它 topic）RocketMQ 未接入，跳过不动。
     *
     * @return 统计：total 扫描数 / sent 成功数 / failed 失败数 / skipped 跳过数
     */
    public Map<String, Integer> dispatchPending(int limit) {
        List<EventOutbox> pending = mapper.selectList(new LambdaQueryWrapper<EventOutbox>()
                .eq(EventOutbox::getState, OutboxState.PENDING.name())
                .orderByAsc(EventOutbox::getId)
                .last("limit " + Math.max(1, limit)));
        int sent = 0;
        int failed = 0;
        int skipped = 0;
        for (EventOutbox row : pending) {
            if (!ApprovalCallbackDispatcher.TOPIC_APPROVAL_CALLBACK.equals(row.getTopic())) {
                skipped++;
                continue;
            }
            if (deliverCallback(row)) {
                row.setState(OutboxState.SENT.name());
                mapper.updateById(row);
                sent++;
            } else {
                row.setRetryCnt(row.getRetryCnt() == null ? 1 : row.getRetryCnt() + 1);
                mapper.updateById(row);
                failed++;
            }
        }
        if (!pending.isEmpty()) {
            log.info("outbox 重试完成: total={}, sent={}, failed={}, skipped={}",
                    pending.size(), sent, failed, skipped);
        }
        return Map.of("total", pending.size(), "sent", sent, "failed", failed, "skipped", skipped);
    }

    /** 解析回调事件信封并投递；解析异常视为投递失败（保留 PENDING 供人工排查）。 */
    private boolean deliverCallback(EventOutbox row) {
        try {
            Map<String, Object> envelope = JsonUtil.toMap(row.getPayload());
            Object payloadObj = envelope.get("payload");
            if (!(payloadObj instanceof Map<?, ?> payload)) {
                log.error("审批回调事件 payload 结构异常，eventId={}", row.getEventId());
                return false;
            }
            return callbackDispatcher.deliver(
                    str(payload.get("bizType")),
                    str(payload.get("bizKey")),
                    longOrNull(envelope.get("tenantId")),
                    Boolean.TRUE.equals(payload.get("approved")),
                    str(payload.get("outcome")),
                    str(payload.get("comment")),
                    str(payload.get("procInstId")));
        } catch (Exception e) {
            log.error("审批回调重试失败，eventId={}", row.getEventId(), e);
            return false;
        }
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Long longOrNull(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }
}

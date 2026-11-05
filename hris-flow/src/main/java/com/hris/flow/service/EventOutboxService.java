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
 * <p>两类事件：① MQ 事件（INSTANCE_STARTED/TASK_COMPLETED/INSTANCE_FINISHED），经
 * {@link OutboxMqPublisher} 投递 RocketMQ（批 12 #25）；② 审批回调事件
 * （{@link ApprovalCallbackDispatcher} Feign 失败落表），由 {@link #dispatchPending(int)}
 * 走同一 dispatcher 重试，成功置 SENT。</p>
 *
 * <p><b>fail-open 与死信</b>：mq-enabled=false（缺省）时 MQ 事件跳过不误标（旧行为，仅落表）；
 * 发送异常保留 PENDING 并 retry_cnt+1，累计达到 {@code hris.flow.outbox.max-retry}（缺省 3）
 * 置 DEAD——当前无告警载体，仅打 ERROR 计数日志（见 docs/19 §8.7-1 遗留说明）。</p>
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
    private final OutboxMqPublisher mqPublisher;

    public EventOutboxService(EventOutboxMapper mapper, ApprovalCallbackDispatcher callbackDispatcher,
                              OutboxMqPublisher mqPublisher) {
        this.mapper = mapper;
        this.callbackDispatcher = callbackDispatcher;
        this.mqPublisher = mqPublisher;
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
     * 重试待发事件（docs/19 §8.7-1；批 12 #25 接入 RocketMQ）：遍历 PENDING 事件——
     * 审批回调事件走 {@link ApprovalCallbackDispatcher#deliver}；MQ 事件经
     * {@link OutboxMqPublisher} 投递。成功置 SENT；失败 retryCnt+1 保留 PENDING，
     * 达到重试上限置 DEAD。mq-enabled=false 时 MQ 事件跳过不误标。
     *
     * @return 统计：total 扫描数 / sent 成功数 / failed 失败数 / skipped 跳过数 / dead 死信数
     */
    public Map<String, Integer> dispatchPending(int limit) {
        List<EventOutbox> pending = mapper.selectList(new LambdaQueryWrapper<EventOutbox>()
                .eq(EventOutbox::getState, OutboxState.PENDING.name())
                .orderByAsc(EventOutbox::getId)
                .last("limit " + Math.max(1, limit)));
        int sent = 0;
        int failed = 0;
        int skipped = 0;
        int dead = 0;
        for (EventOutbox row : pending) {
            boolean ok;
            if (ApprovalCallbackDispatcher.TOPIC_APPROVAL_CALLBACK.equals(row.getTopic())) {
                ok = deliverCallback(row);
            } else if (mqPublisher.isEnabled()) {
                ok = mqPublisher.publish(row);
            } else {
                mqPublisher.warnDisabledOnce();
                skipped++;
                continue;
            }
            if (ok) {
                row.setState(OutboxState.SENT.name());
                mapper.updateById(row);
                sent++;
            } else {
                int retry = row.getRetryCnt() == null ? 1 : row.getRetryCnt() + 1;
                row.setRetryCnt(retry);
                if (retry >= mqPublisher.maxRetry()) {
                    row.setState(OutboxState.DEAD.name());
                    mapper.updateById(row);
                    dead++;
                    log.error("outbox 事件进入死信（重试 {} 次仍未成功，无告警载体仅计数）: eventId={}, topic={}",
                            retry, row.getEventId(), row.getTopic());
                } else {
                    mapper.updateById(row);
                    failed++;
                }
            }
        }
        if (!pending.isEmpty()) {
            log.info("outbox 重试完成: total={}, sent={}, failed={}, skipped={}, dead={}",
                    pending.size(), sent, failed, skipped, dead);
        }
        return Map.of("total", pending.size(), "sent", sent, "failed", failed,
                "skipped", skipped, "dead", dead);
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

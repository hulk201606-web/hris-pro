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
 * 事件出站服务（FLW-F-012）。本地事务内写 flow_ext_event_outbox，异步发 RocketMQ。
 *
 * <p>MVP：先落表 + 预留发送入口 {@link #dispatchPending(int)}。RocketMQ 未就绪，发送端暂空转
 * （返回待发记录供后续接入 producer，不误标 SENT 避免丢事件）。消息信封遵循 docs/07 §4。</p>
 */
@Service
public class EventOutboxService {

    private static final Logger log = LoggerFactory.getLogger(EventOutboxService.class);

    /** 实例启动事件 topic（docs/07 §4：hris-{域}-{事件过去式}） */
    public static final String TOPIC_INSTANCE_STARTED = "hris-flow-instance-started";
    public static final String TOPIC_TASK_COMPLETED = "hris-flow-task-completed";
    public static final String TOPIC_INSTANCE_FINISHED = "hris-flow-instance-finished";

    private final EventOutboxMapper mapper;

    public EventOutboxService(EventOutboxMapper mapper) {
        this.mapper = mapper;
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
     * 预留发送入口：读取待发事件，接入 RocketMQ producer 后在此逐条发送并标 SENT。
     * MVP 阶段仅返回待发记录并告警提示（不发送、不误标），避免事件静默丢失。
     */
    public List<EventOutbox> dispatchPending(int limit) {
        List<EventOutbox> pending = mapper.selectList(new LambdaQueryWrapper<EventOutbox>()
                .eq(EventOutbox::getState, OutboxState.PENDING.name())
                .orderByAsc(EventOutbox::getId)
                .last("limit " + Math.max(1, limit)));
        if (!pending.isEmpty()) {
            log.warn("事件出站已落 {} 条，RocketMQ producer 未接入，暂不发送", pending.size());
        }
        return pending;
    }
}

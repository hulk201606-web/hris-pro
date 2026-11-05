package com.hris.flow.service;

import com.hris.flow.entity.EventOutbox;
import com.hris.flow.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * outbox → RocketMQ 发布器（批 12 #25 / FLW-F-012）。
 *
 * <p>消息体（JSON）：{@code {eventId, type, bizKey, payload, tenantId}}，其中 type 取事件 topic，
 * payload 为 outbox 信封里的业务载荷。发送成功由 {@link EventOutboxService} 置 SENT。</p>
 *
 * <p><b>fail-open</b>：{@code hris.flow.outbox.mq-enabled} 缺省 false（本机/未部署 RocketMQ 的
 * 环境不报错）；关闭时保持旧行为（仅落表，跳过发送）。开启后单条发送异常只记日志并返回 false，
 * 由调用方保留 PENDING 递增重试，绝不阻断审批主流程。</p>
 */
@Component
public class OutboxMqPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxMqPublisher.class);

    private final boolean enabled;
    private final String topic;
    private final int maxRetry;
    private final OutboxMqSender sender;
    private final AtomicBoolean disabledWarned = new AtomicBoolean(false);

    /** Spring 装配用：sender 缺失（未启用 MQ）时以告警空实现占位。 */
    @org.springframework.beans.factory.annotation.Autowired
    public OutboxMqPublisher(@Value("${hris.flow.outbox.mq-enabled:false}") boolean enabled,
                             @Value("${hris.flow.outbox.topic:hris-flow-events}") String topic,
                             @Value("${hris.flow.outbox.max-retry:3}") int maxRetry,
                             ObjectProvider<OutboxMqSender> senderProvider) {
        this(enabled, topic, maxRetry, senderProvider.getIfAvailable(() -> (t, k, b) -> {
            throw new IllegalStateException("RocketMQ sender 未装配（mq-enabled=false 或 producer 未启动）");
        }));
    }

    /** 单测用构造。 */
    public OutboxMqPublisher(boolean enabled, String topic, int maxRetry, OutboxMqSender sender) {
        this.enabled = enabled;
        this.topic = topic;
        this.maxRetry = maxRetry < 1 ? 3 : maxRetry;
        this.sender = sender;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String topic() {
        return topic;
    }

    public int maxRetry() {
        return maxRetry;
    }

    /** mq-enabled=false 时只告警一次，避免每轮调度刷日志。 */
    public void warnDisabledOnce() {
        if (disabledWarned.compareAndSet(false, true)) {
            log.warn("事件总线未启用（hris.flow.outbox.mq-enabled=false），outbox 仅落表不发送；"
                    + "部署 RocketMQ 后置 true 并配置 name-server 即可恢复投递");
        }
    }

    /**
     * 发布单条 outbox 事件。
     *
     * @return true=已投递；false=发送失败（含配置关闭），调用方保留 PENDING 递增重试
     */
    public boolean publish(EventOutbox row) {
        if (!enabled) {
            warnDisabledOnce();
            return false;
        }
        try {
            sender.send(topic, row.getEventId(), bodyOf(row));
            return true;
        } catch (Exception e) {
            log.warn("事件发送失败，保留待重试: eventId={}, topic={}, cause={}",
                    row.getEventId(), row.getTopic(), e.getMessage());
            return false;
        }
    }

    /** 组装消息体：eventId/type/bizKey/payload/tenantId。 */
    String bodyOf(EventOutbox row) {
        Map<String, Object> envelope = JsonUtil.toMap(row.getPayload());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", row.getEventId());
        body.put("type", row.getTopic());
        body.put("bizKey", envelope.get("bizKey"));
        body.put("payload", envelope.get("payload"));
        body.put("tenantId", envelope.get("tenantId"));
        return JsonUtil.toJson(body);
    }
}

package com.hris.flow.service;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * RocketMQ 投递实现（批 12 #25）。producer 由 {@link com.hris.flow.config.OutboxMqConfig}
 * 在 mq-enabled=true 时装配；未装配时 send 抛异常，由 {@link OutboxMqPublisher} 吞掉并返回 false
 * （fail-open：事件保留 PENDING，不阻断审批）。
 */
@Component
public class RocketMqOutboxSender implements OutboxMqSender {

    /** 消息 tag：全部事件共用，消费端可按 tag 过滤。 */
    private static final String TAG = "FLOW_EVENT";

    private final ObjectProvider<DefaultMQProducer> producerProvider;

    public RocketMqOutboxSender(ObjectProvider<DefaultMQProducer> producerProvider) {
        this.producerProvider = producerProvider;
    }

    @Override
    public void send(String topic, String key, String body) throws Exception {
        DefaultMQProducer producer = producerProvider.getIfAvailable();
        if (producer == null) {
            throw new IllegalStateException("RocketMQ producer 未装配（hris.flow.outbox.mq-enabled=false）");
        }
        Message message = new Message(topic, TAG, key, body.getBytes(StandardCharsets.UTF_8));
        producer.send(message, 3000);
    }
}

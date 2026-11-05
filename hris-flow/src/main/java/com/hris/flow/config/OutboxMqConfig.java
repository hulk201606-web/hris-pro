package com.hris.flow.config;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 事件总线 producer 装配（批 12 #25 / FLW-F-012）。
 *
 * <p>rocketmq-spring-boot-starter 2.3.x 的自动配置只登记在 META-INF/spring.factories，
 * Boot 4 不再加载，故此处手动构建 {@link DefaultMQProducer}，并只在
 * {@code hris.flow.outbox.mq-enabled=true} 时创建——本机/未部署 RocketMQ 的环境不装配、不报错。</p>
 *
 * <p><b>fail-open</b>：start() 失败只告警不抛（服务照常启动，发送失败由发布器返回 false，
 * 事件保留 PENDING 重试），绝不因为 MQ 不可用阻断审批主链路。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "hris.flow.outbox", name = "mq-enabled", havingValue = "true")
public class OutboxMqConfig {

    private static final Logger log = LoggerFactory.getLogger(OutboxMqConfig.class);

    @Bean(destroyMethod = "shutdown")
    public DefaultMQProducer outboxMqProducer(
            @Value("${hris.flow.outbox.name-server:${ROCKETMQ_NAMESRV:127.0.0.1:9876}}") String nameServer,
            @Value("${hris.flow.outbox.producer-group:hris-flow-outbox-producer}") String producerGroup) {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(3000);
        producer.setRetryTimesWhenSendFailed(1);
        try {
            producer.start();
            log.info("RocketMQ producer 已启动: group={}, nameServer={}", producerGroup, nameServer);
        } catch (Exception e) {
            log.warn("RocketMQ producer 启动失败（fail-open，事件保留 PENDING 待重试）: nameServer={}, cause={}",
                    nameServer, e.getMessage());
        }
        return producer;
    }
}

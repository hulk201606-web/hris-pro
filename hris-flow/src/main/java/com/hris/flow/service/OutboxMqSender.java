package com.hris.flow.service;

/**
 * 出站事件投递通道（FLW-F-012）：抽象一层便于单测替换，生产实现为 RocketMQ。
 */
@FunctionalInterface
public interface OutboxMqSender {

    /**
     * 发送一条事件消息。
     *
     * @param topic 主题（如 hris-flow-events）
     * @param key   消息 key（eventId，用于幂等/追溯）
     * @param body  JSON 消息体
     * @throws Exception 发送失败（调用方按失败保留 PENDING 处理）
     */
    void send(String topic, String key, String body) throws Exception;
}

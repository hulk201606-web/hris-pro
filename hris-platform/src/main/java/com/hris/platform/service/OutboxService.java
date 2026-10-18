package com.hris.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.platform.entity.SysOutboxMessage;
import com.hris.platform.mapper.SysOutboxMessageMapper;
import com.hris.platform.util.JsonUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 全局事件表（发件箱模式，docs/12 §5.10）。
 *
 * <p>「本地事务 + 事件表」的事务消息模板：业务服务与 outbox 同库同事务 enqueue，
 * 独立调度器轮询 PENDING 状态投递 MQ 后置 SENT（投递失败按 retry_cnt/next_retry_at 退避）。
 * 本增量提供写入与状态机；MQ 投递调度器待 RocketMQ 接入后实现。</p>
 */
@Service
public class OutboxService {

    private static final String STATE_PENDING = "PENDING";
    private static final String STATE_SENT = "SENT";
    private static final String STATE_DEAD = "DEAD";

    private final SysOutboxMessageMapper mapper;

    public OutboxService(SysOutboxMessageMapper mapper) {
        this.mapper = mapper;
    }

    /** 入队事件（幂等键 eventId）。 */
    public SysOutboxMessage enqueue(String eventId, String topic, Object payload) {
        SysOutboxMessage m = new SysOutboxMessage();
        m.setEventId(eventId);
        m.setTopic(topic);
        m.setPayload(JsonUtil.toJson(payload));
        m.setState(STATE_PENDING);
        m.setRetryCnt(0);
        m.setCreatedAt(LocalDateTime.now());
        mapper.insert(m);
        return m;
    }

    /** 标记已投递。 */
    public void markSent(Long id) {
        SysOutboxMessage m = mapper.selectById(id);
        if (m != null) {
            m.setState(STATE_SENT);
            mapper.updateById(m);
        }
    }

    /** 标记死信（超过最大重试）。 */
    public void markDead(Long id) {
        SysOutboxMessage m = mapper.selectById(id);
        if (m != null) {
            m.setState(STATE_DEAD);
            mapper.updateById(m);
        }
    }

    /** 查询待投递事件（调度器轮询入口，MVP 预留）。 */
    public List<SysOutboxMessage> listPending(int limit) {
        return mapper.selectList(new LambdaQueryWrapper<SysOutboxMessage>()
                .eq(SysOutboxMessage::getState, STATE_PENDING)
                .orderByAsc(SysOutboxMessage::getCreatedAt)
                .last("LIMIT " + limit));
    }
}

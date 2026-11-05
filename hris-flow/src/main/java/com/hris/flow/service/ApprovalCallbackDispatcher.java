package com.hris.flow.service;

import com.hris.flow.client.AttApprovalCallbackClient;
import com.hris.flow.client.EmpApprovalCallbackClient;
import com.hris.flow.client.OrgApprovalCallbackClient;
import com.hris.flow.dto.ApprovalCallbackRequest;
import com.hris.flow.entity.EventOutbox;
import com.hris.flow.enums.OutboxState;
import com.hris.flow.mapper.EventOutboxMapper;
import com.hris.flow.util.JsonUtil;
import com.hris.common.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 审批回调分发器（docs/19 §8.3-1 断链修复：flow 审批终态 → 业务域）。
 *
 * <p>按 bizType 前缀路由：ATT_* → hris-attendance、EMP_* → hris-employee、ORG_* → hris-org；
 * 其它前缀（RCS_/CMP_/PRF_ 等）暂无回调端点，落 flow_ext_event_outbox（PENDING）不报错。</p>
 *
 * <p>失败处理：Feign 调用异常时 log.error 并把回调事件落 outbox（PENDING）供
 * {@link EventOutboxService#dispatchPending(int)} 重试，绝不向上抛——审批动作本身不能因回调失败而失败。</p>
 */
@Service
public class ApprovalCallbackDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ApprovalCallbackDispatcher.class);

    /** 审批回调事件 topic（重试通道复用同一信封结构） */
    public static final String TOPIC_APPROVAL_CALLBACK = "hris-flow-approval-callback";

    private final AttApprovalCallbackClient attClient;
    private final EmpApprovalCallbackClient empClient;
    private final OrgApprovalCallbackClient orgClient;
    private final EventOutboxMapper outboxMapper;

    public ApprovalCallbackDispatcher(AttApprovalCallbackClient attClient,
                                      EmpApprovalCallbackClient empClient,
                                      OrgApprovalCallbackClient orgClient,
                                      EventOutboxMapper outboxMapper) {
        this.attClient = attClient;
        this.empClient = empClient;
        this.orgClient = orgClient;
        this.outboxMapper = outboxMapper;
    }

    /**
     * 审批终态触发入口（approve/reject/withdraw 调用）。任何异常都被吞掉并落 outbox，
     * 不影响调用方事务语义。
     */
    public void dispatchOnTerminal(String bizType, String bizKey, Long tenantId, boolean approved,
                                   String outcome, String comment, String procInstId) {
        try {
            boolean routed = deliver(bizType, bizKey, tenantId, approved, outcome, comment, procInstId);
            if (!routed) {
                // 无回调端点的 bizType：落 outbox PENDING（供 MQ 接入后消费/人工重试），不报错
                log.info("bizType 无回调端点，事件落 outbox: bizType={}, bizKey={}", bizType, bizKey);
                savePending(bizType, bizKey, tenantId, approved, outcome, comment, procInstId);
            }
        } catch (Exception e) {
            log.error("审批回调失败，已落 outbox 待重试: bizType={}, bizKey={}", bizType, bizKey, e);
            try {
                savePending(bizType, bizKey, tenantId, approved, outcome, comment, procInstId);
            } catch (Exception ex) {
                log.error("审批回调事件落 outbox 失败: bizKey={}", bizKey, ex);
            }
        }
    }

    /**
     * 纯投递：可路由则同步 Feign 调用并返回 true；无可路由端点返回 false。
     * Feign 异常向上抛（由调用方决定兜底策略）；租户/链路上下文按事件值临时还原，
     * 结束后恢复调用线程原值，不污染当前上下文。
     */
    public boolean deliver(String bizType, String bizKey, Long tenantId, boolean approved,
                           String outcome, String comment, String procInstId) {
        if (bizType == null || bizType.isBlank()) {
            return false;
        }
        ApprovalCallbackRequest body = ApprovalCallbackRequest.of(
                bizType, bizKey, approved, outcome, comment, procInstId, tenantId);
        if (bizType.startsWith("ATT_")) {
            callWithTenant(tenantId, () -> attClient.callback(body));
            return true;
        }
        if (bizType.startsWith("EMP_")) {
            callWithTenant(tenantId, () -> empClient.callback(body));
            return true;
        }
        if (bizType.startsWith("ORG_")) {
            callWithTenant(tenantId, () -> orgClient.callback(body));
            return true;
        }
        return false;
    }

    /**
     * 临时切换租户/链路上下文执行调用（outbox 重试线程无请求上下文，必须显式还原），
     * finally 恢复原值，避免污染请求线程上下文。
     */
    private void callWithTenant(Long tenantId, Runnable call) {
        Long oldTenant = TraceContext.getTenantId();
        String oldTrace = TraceContext.getTraceId();
        if (tenantId != null) {
            TraceContext.setTenantId(tenantId);
        }
        try {
            call.run();
        } finally {
            TraceContext.setTenantId(oldTenant);
            if (oldTrace != null) {
                TraceContext.setTraceId(oldTrace);
            }
        }
    }

    /**
     * 回调事件落 outbox（PENDING）。信封结构与 {@link EventOutboxService#publish} 一致，
     * 供 dispatchPending 重试时解析（直接走 mapper，避免与 EventOutboxService 构造循环依赖）。
     */
    public void savePending(String bizType, String bizKey, Long tenantId, boolean approved,
                            String outcome, String comment, String procInstId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bizType", bizType);
        payload.put("bizKey", bizKey);
        payload.put("approved", approved);
        payload.put("outcome", outcome);
        payload.put("comment", comment);
        payload.put("procInstId", procInstId);

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString().replace("-", ""));
        envelope.put("topic", TOPIC_APPROVAL_CALLBACK);
        envelope.put("tenantId", tenantId);
        envelope.put("traceId", TraceContext.getTraceId());
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("bizKey", bizKey);
        envelope.put("payload", payload);

        EventOutbox row = new EventOutbox();
        row.setEventId((String) envelope.get("eventId"));
        row.setTopic(TOPIC_APPROVAL_CALLBACK);
        row.setPayload(JsonUtil.toJson(envelope));
        row.setState(OutboxState.PENDING.name());
        row.setRetryCnt(0);
        outboxMapper.insert(row);
    }
}

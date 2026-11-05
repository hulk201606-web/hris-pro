package com.hris.rag.service;

import com.hris.common.api.R;
import com.hris.rag.client.FlowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程中台集成门面（KBD_PUBLISH 发起，docs/19 §9 遗留项）。
 *
 * <p>fail-open 契约（同考勤域 FlowIntegration）：flow 服务不可用或发起失败时，
 * log.warn 后返回 null，不阻断提交——版本保留 PENDING 待审，
 * 最终状态由 flow 回调 /internal/kb/approval-callback 驱动。</p>
 */
@Service
public class FlowIntegration {

    private static final Logger log = LoggerFactory.getLogger(FlowIntegration.class);

    private final FlowClient flowClient;

    public FlowIntegration(FlowClient flowClient) {
        this.flowClient = flowClient;
    }

    /** 发起流程实例，返回流程实例 ID；flow 未就绪时返回 null（降级，不阻断业务）。 */
    public String start(String bizType, String bizKey, Map<String, Object> payload) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("bizType", bizType);
            request.put("bizKey", bizKey);
            request.put("payload", payload);
            R<Map<String, Object>> resp = flowClient.startInstance(request);
            Map<String, Object> data = resp == null ? null : resp.getData();
            Object id = data == null ? null : data.get("instanceId");
            return id == null ? null : String.valueOf(id);
        } catch (Exception e) {
            log.warn("flow 服务未就绪，单据仅落单不发起流程: bizKey={}, err={}", bizKey, e.getMessage());
            return null;
        }
    }
}

package com.hris.attendance.service;

import com.hris.attendance.client.FlowClient;
import com.hris.common.api.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程中台集成门面（ATT_LEAVE / ATT_OT / ATT_TRIP / ATT_FIX）。
 *
 * <p>MVP 阶段 flow 服务可能未就绪：发起失败降级为「仅落单」，不阻断单据创建
 * （同 hris-org / hris-employee 的 FlowIntegration 做法）。单据最终状态
 * 由 flow 回调 {@code /internal/att/approval-callback} 驱动。</p>
 */
@Service
public class FlowIntegration {

    private static final Logger log = LoggerFactory.getLogger(FlowIntegration.class);

    private final FlowClient flowClient;

    public FlowIntegration(FlowClient flowClient) {
        this.flowClient = flowClient;
    }

    /** 发起流程实例，返回流程实例 ID；flow 未就绪时返回 null（降级）。 */
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

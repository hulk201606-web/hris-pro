package com.hris.employee.integration;

import com.hris.common.api.R;
import com.hris.employee.client.FlowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 流程中台集成门面（EMP_ONBOARD/REGULAR/TRANSFER/RESIGN）。
 * MVP 阶段 flow 服务未就绪：发起失败降级为「仅落单」，不阻断生命周期单创建（同 org 域 FlowIntegration 做法）。
 */
@Service
public class FlowIntegration {

    private static final Logger log = LoggerFactory.getLogger(FlowIntegration.class);

    private final FlowClient flowClient;

    public FlowIntegration(FlowClient flowClient) {
        this.flowClient = flowClient;
    }

    /**
     * 发起流程实例。返回流程实例 ID；flow 未就绪时返回 null（降级）。
     *
     * @param initiator 操作者用户 ID（TraceContext 取得）；Feign 系统发起无登录用户时
     *                  传 null，flow 侧会以审批人解析值兜底，避免 ${initiator} 死任务
     */
    public String start(String bizType, String bizKey, Long tenantId, Map<String, Object> payload,
                        Long initiator) {
        try {
            Map<String, Object> request = new java.util.HashMap<>();
            request.put("bizType", bizType);
            request.put("bizKey", bizKey);
            request.put("tenantId", tenantId);
            request.put("payload", payload);
            if (initiator != null) {
                request.put("initiator", initiator);
            }
            R<Map<String, Object>> resp = flowClient.startInstance(request);
            Map<String, Object> data = resp == null ? null : resp.getData();
            Object id = data == null ? null : data.get("instanceId");
            return id == null ? null : String.valueOf(id);
        } catch (Exception e) {
            log.warn("flow 服务未就绪，生命周期单仅落单不发起流程: bizKey={}, err={}", bizKey, e.getMessage());
            return null;
        }
    }
}

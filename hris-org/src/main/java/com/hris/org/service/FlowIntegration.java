package com.hris.org.service;

import com.hris.common.api.R;
import com.hris.org.client.FlowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 流程中台集成门面。MVP 阶段 flow 服务未就绪：发起失败降级为「仅落单」，不阻断组织调整申请。
 * flow 服务落地后移除降级分支，改为强一致调用 + 事务消息兜底（docs/06 §4）。
 */
@Service
public class FlowIntegration {

    private static final Logger log = LoggerFactory.getLogger(FlowIntegration.class);

    private final FlowClient flowClient;

    public FlowIntegration(FlowClient flowClient) {
        this.flowClient = flowClient;
    }

    /**
     * 发起组织调整流程实例。返回流程实例 ID；flow 未就绪时返回 null（降级）。
     */
    public String startOrgAdjust(Map<String, Object> request) {
        try {
            R<Map<String, Object>> resp = flowClient.startInstance(request);
            Map<String, Object> data = resp == null ? null : resp.getData();
            Object id = data == null ? null : data.get("instanceId");
            return id == null ? null : String.valueOf(id);
        } catch (Exception e) {
            log.warn("flow 服务未就绪，组织调整申请仅落单不发起流程: {}", e.getMessage());
            return null;
        }
    }
}

package com.hris.platform.scheduler;

import com.hris.common.trace.TraceContext;
import com.hris.platform.client.OrgOpsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 组织版本到期自动激活调度任务（批 11 #23 / ORG-F-004）。
 *
 * <p>调用 {@code hris-org} 的 {@code /internal/org/versions/activate-due}：
 * 把生效日已到的 DRAFT 组织版本批次激活（历史 ACTIVE 置 SUPERSEDED）。
 * 幂等——无到期批次时返回 activated=0。</p>
 */
@Component
public class OrgVersionActivateHandler implements TaskHandler {

    /** 与 sys_scheduler_task_registry.handler 对应的标识。 */
    public static final String KEY = "ORG_VERSION_ACTIVATE_DUE";

    private static final Logger log = LoggerFactory.getLogger(OrgVersionActivateHandler.class);

    private final OrgOpsClient orgOpsClient;

    public OrgVersionActivateHandler(OrgOpsClient orgOpsClient) {
        this.orgOpsClient = orgOpsClient;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String description() {
        return "组织版本到期自动激活（DRAFT -> ACTIVE，历史版本 SUPERSEDED）";
    }

    @Override
    public String execute() {
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("调度任务缺少租户上下文");
        }
        var resp = orgOpsClient.activateDueVersions(tenantId, null);
        if (resp == null || resp.getCode() != 0) {
            throw new IllegalStateException("组织版本激活失败: " + (resp == null ? "无响应" : resp.getMessage()));
        }
        Object activated = resp.getData() == null ? null : resp.getData().get("activated");
        log.info("组织版本到期激活触发完成 tenant={} activated={}", tenantId, activated);
        return "activated=" + activated;
    }
}

package com.hris.platform.scheduler;

import com.hris.common.trace.TraceContext;
import com.hris.platform.client.FlowOpsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 流程超时任务升级改派调度（docs/19 §10.5 C 类一期深化，PLT 侧接入）。
 *
 * <p>flow 侧 {@code POST /internal/flow/tasks/escalate-overdue} 已就绪（幂等，
 * 已升级任务按 task_action 判断不重复），本任务由 platform 调度中心按注册项
 * cron（默认每 30 分钟）触发，跨域 Feign 调用并返回摘要。</p>
 *
 * <p>租户上下文已由 SchedulerRegistry 注入（execute 内不要自行切换），
 * 经 traceFeignInterceptor 透传给下游。异常向调度器透传（记 FAILED 触发重试）。</p>
 */
@Component
public class TimeoutEscalationHandler implements TaskHandler {

    /** 与 sys_scheduler_task_registry.handler 对应的标识。 */
    public static final String KEY = "TIMEOUT_ESCALATION";

    private static final Logger log = LoggerFactory.getLogger(TimeoutEscalationHandler.class);

    private final FlowOpsClient flowOpsClient;

    public TimeoutEscalationHandler(FlowOpsClient flowOpsClient) {
        this.flowOpsClient = flowOpsClient;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String description() {
        return "流程超时升级：扫描 overdue 任务按 escalateAfterH/escalate_to 升级改派并留痕 ESCALATE";
    }

    @Override
    public String execute() throws Exception {
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("调度线程缺少租户上下文，拒绝执行（防跨租户误升级）");
        }
        var resp = flowOpsClient.escalateOverdue();
        if (resp == null || resp.getData() == null) {
            throw new IllegalStateException("流程域升级返回为空");
        }
        if (resp.getCode() != 0) {
            throw new IllegalStateException("流程域升级返回失败: code=" + resp.getCode() + " msg=" + resp.getMessage());
        }
        return summary(resp.getData());
    }

    private String summary(java.util.Map<String, Integer> data) {
        int scanned = data.getOrDefault("scanned", 0);
        int escalated = data.getOrDefault("escalated", 0);
        int skipped = data.getOrDefault("skippedIdempotent", 0);
        log.info("超时任务升级完成: tenant={}, scanned={}, escalated={}, skippedIdempotent={}",
                TraceContext.getTenantId(), scanned, escalated, skipped);
        return "扫描 " + scanned + "，升级 " + escalated + "，幂等跳过 " + skipped;
    }
}

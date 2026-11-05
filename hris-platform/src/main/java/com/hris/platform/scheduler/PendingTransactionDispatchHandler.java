package com.hris.platform.scheduler;

import com.hris.common.trace.TraceContext;
import com.hris.platform.client.EmployeeOpsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 待生效事务批量落账调度任务（docs/19 §9.1，一期「全流程审批闭环」最后一块）。
 *
 * <p>审批通过只生成 PENDING 待生效事务（按 effect_date 到期生效），此前无任何
 * 调度调用 {@code applyDue}，事务永远不落账——员工审批通过后状态不推进、账号不创建。
 * 本任务由 platform 调度中心按注册项 cron 触发，跨域 Feign 调 hris-employee
 * 的 {@code /internal/emp/pending-transactions/apply-due}。</p>
 *
 * <p>租户上下文已由 SchedulerRegistry 注入（execute 内不要自行切换），
 * 经 traceFeignInterceptor 透传给下游。落账截止日取今天：到期事务当天生效。</p>
 */
@Component
public class PendingTransactionDispatchHandler implements TaskHandler {

    /** 与 sys_scheduler_task_registry.handler 对应的标识。 */
    public static final String KEY = "PENDING_TRANSACTION_APPLY";

    private static final Logger log = LoggerFactory.getLogger(PendingTransactionDispatchHandler.class);

    private final EmployeeOpsClient employeeOpsClient;

    public PendingTransactionDispatchHandler(EmployeeOpsClient employeeOpsClient) {
        this.employeeOpsClient = employeeOpsClient;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String description() {
        return "待生效事务落账：按生效日应用员工入转调离的 PENDING 事务（状态推进/账号联动）";
    }

    @Override
    public String execute() throws Exception {
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("调度线程缺少租户上下文，拒绝执行（防跨租户误落账）");
        }
        String effectDate = LocalDate.now().toString();
        var resp = employeeOpsClient.applyDue(tenantId, effectDate);
        Integer applied = resp == null || resp.getData() == null ? 0 : resp.getData();
        if (resp != null && resp.getCode() != 0) {
            throw new IllegalStateException("员工域落账返回失败: code=" + resp.getCode() + " msg=" + resp.getMessage());
        }
        log.info("待生效事务落账完成: tenant={}, effectDate={}, applied={}", tenantId, effectDate, applied);
        return "落账 " + applied + " 条（effectDate<=" + effectDate + "）";
    }
}

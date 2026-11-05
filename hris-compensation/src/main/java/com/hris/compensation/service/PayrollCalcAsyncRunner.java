package com.hris.compensation.service;

import com.hris.common.trace.TraceContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步算薪执行器（批 8 #18a）：@Async 专用线程池（payrollCalcExecutor，有界队列防雪崩）。
 *
 * <p>ThreadLocal 隔离：TraceContext 为 ThreadLocal 实现，异步线程不继承请求上下文，
 * 调用方（PayrollCalcService.calcRunAsync）在提交时捕获 tenant/trace/user 并经参数传入，
 * 线程池线程执行前还原、finally 清理，保证租户插件与 Feign 头透传在异步线程内生效。</p>
 *
 * <p>循环依赖：Runner 依赖 CalcService 执行计算，CalcService 依赖 Runner 提交任务，
 * 构造注入经 @Lazy 打断（仅提交点代理化，运行期无实际代理开销）。</p>
 */
@Component
public class PayrollCalcAsyncRunner {

    private final PayrollCalcService calcService;

    public PayrollCalcAsyncRunner(@Lazy PayrollCalcService calcService) {
        this.calcService = calcService;
    }

    /**
     * 异步执行整批算薪。prevStatus 用于异常时回滚 run 状态（CALCULATING → prev）。
     */
    @Async("payrollCalcExecutor")
    public void run(Long runId, String prevStatus, Long tenantId, String traceId, Long userId) {
        if (tenantId != null) {
            TraceContext.setTenantId(tenantId);
        }
        if (userId != null) {
            TraceContext.setUserId(userId);
        }
        if (traceId != null && !traceId.isBlank()) {
            TraceContext.setTraceId(traceId);
        }
        try {
            calcService.completeCalc(runId, prevStatus);
        } finally {
            TraceContext.clear();
        }
    }
}

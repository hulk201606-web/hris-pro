package com.hris.platform.scheduler;

import com.hris.platform.service.ReportSubscriptionService;
import org.springframework.stereotype.Component;

/**
 * 报表订阅执行器（PLT-F-005，批11#22）。
 *
 * <p>一行订阅对应一个注册任务（task_key=RPT_SUB_{id}）。调度线程已由
 * {@link SchedulerRegistry} 还原租户上下文，这里只按 task_key 解析订阅并执行
 * 「创建导出任务 + 通知发送」。task_key 非法视为配置错误直接失败，不静默跳过。</p>
 */
@Component
public class ReportSubscriptionDispatchHandler implements TaskHandler {

    public static final String KEY = "REPORT_SUBSCRIPTION_DISPATCH";

    private final ReportSubscriptionService subscriptionService;

    public ReportSubscriptionDispatchHandler(ReportSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String description() {
        return "报表订阅：按订阅触发导出任务并发送通知";
    }

    @Override
    public String execute() {
        throw new IllegalStateException("报表订阅必须按 task_key 执行，禁止无参调用");
    }

    @Override
    public String execute(String taskKey) {
        Long subscriptionId = ReportSubscriptionService.subscriptionId(taskKey);
        if (subscriptionId == null) {
            throw new IllegalStateException("非法报表订阅任务键: " + taskKey);
        }
        return subscriptionService.execute(subscriptionId);
    }
}

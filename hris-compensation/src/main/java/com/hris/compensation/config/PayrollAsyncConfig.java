package com.hris.compensation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步算薪线程池（批 8 #18a）。
 *
 * <p>容量口径：核心 2 / 上限 4 / 队列 100（有界防雪崩，队列满即拒绝并由调用方
 * 回滚 run 状态后报「计算队列已满」）。拒绝策略 AbortPolicy（TaskRejectedException
 * 上抛，calcRunAsync 捕获回滚）。允许核心线程超时回收，空闲 60s 销毁。</p>
 */
@Configuration
@EnableAsync
public class PayrollAsyncConfig {

    @Bean("payrollCalcExecutor")
    public ThreadPoolTaskExecutor payrollCalcExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("payroll-calc-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}

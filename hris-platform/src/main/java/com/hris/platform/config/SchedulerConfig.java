package com.hris.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 调度线程池（PLT-F-011）。
 *
 * <p>选型说明：需求写的是 XXL-JOB，但引入独立调度中心需要额外部署与运维，
 * 而本项目当前只需要「按 cron 触发少量域内任务 + 看板 + 失败重试」。
 * 因此用 Spring 编程式 {@link ThreadPoolTaskScheduler} 驱动注册表，
 * 保持 cron 语义与 XXL-JOB 一致，未来迁移时注册表数据可复用。见 ADR-006。</p>
 *
 * <p>不用 {@code @EnableScheduling}：那会让任何模块随手加的 @Scheduled
 * 都隐式生效，无法按租户启停、无法记录执行日志。这里全部走显式注册。</p>
 */
@Configuration
public class SchedulerConfig {

    public static final String SCHEDULER_BEAN = "hrisTaskScheduler";

    @Bean(name = SCHEDULER_BEAN)
    public ThreadPoolTaskScheduler hrisTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("hris-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.setErrorHandler(t -> {
            // 兜底：正常路径的异常由 SchedulerRegistry 捕获并落日志，
            // 这里只处理「连日志写入都失败」的极端情况，避免线程静默死亡。
            org.slf4j.LoggerFactory.getLogger(SchedulerConfig.class)
                    .error("Unhandled scheduler error", t);
        });
        return scheduler;
    }
}

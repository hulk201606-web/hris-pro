package com.hris.platform.scheduler;

/**
 * 调度任务处理器。
 *
 * <p>注册表里的 handler 字段必须能映射到某个实现，否则任务会在执行时被判为
 * FAILED 并写入日志——不静默跳过。这样配错处理器能被看板直接发现。</p>
 *
 * <p>实现方运行在调度线程中，租户上下文已由 {@code SchedulerRegistry} 注入，
 * 不要在实现里自行切换租户。</p>
 */
public interface TaskHandler {

    /** 与 sys_scheduler_task_registry.handler 对应的标识。 */
    String key();

    /** 人类可读说明，用于任务看板的处理器下拉。 */
    String description();

    /**
     * 执行一次，返回写入执行日志的摘要。
     *
     * @throws Exception 任何异常都会被调度器记为 FAILED 并触发重试
     */
    String execute() throws Exception;

    /**
     * 带任务键执行（批11#22 报表订阅）：同一 handler 服务多行订阅时，靠 task_key
     * 区分「本次该跑哪条订阅」。默认忽略 taskKey，兼容既有单例任务。
     */
    default String execute(String taskKey) throws Exception {
        return execute();
    }
}

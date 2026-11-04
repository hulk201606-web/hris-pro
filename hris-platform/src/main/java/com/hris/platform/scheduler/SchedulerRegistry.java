package com.hris.platform.scheduler;

import com.hris.common.trace.TraceContext;
import com.hris.platform.config.SchedulerConfig;
import com.hris.platform.dto.SchedulerDtos.RunResponse;
import com.hris.platform.entity.SysSchedulerTaskLog;
import com.hris.platform.entity.SysSchedulerTaskRegistry;
import com.hris.platform.mapper.SysSchedulerTaskLogMapper;
import com.hris.platform.mapper.SysSchedulerTaskRegistryMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 注册表驱动的动态调度器（PLT-F-011）。
 *
 * <p>三条硬约束，都是踩过的坑：</p>
 * <ol>
 *   <li><b>租户上下文</b>：调度线程不是 HTTP 线程，TraceContext 为空。
 *       加载注册项须绕过租户插件，执行 handler 前必须按注册项的 tenant_id
 *       重建上下文，并在 finally 清理，否则业务 SQL 全部落到 tenant_id=0。</li>
 *   <li><b>不静默跳过</b>：handler 配错、cron 非法、执行抛异常都要留下日志或
 *       告警，宁可让看板显示 FAILED，也不能让人以为任务跑过了。</li>
 *   <li><b>失败重试有上限</b>：最多 3 次，退避 5/20/45 秒；配置类错误
 *       （handler 不存在）不重试，重试解决不了配置错误。</li>
 * </ol>
 */
@Component
public class SchedulerRegistry {

    private static final Logger log = LoggerFactory.getLogger(SchedulerRegistry.class);
    private static final int MAX_RETRY = 3;
    private static final int MESSAGE_LIMIT = 500;

    private final ThreadPoolTaskScheduler scheduler;
    private final SysSchedulerTaskRegistryMapper registryMapper;
    private final SysSchedulerTaskLogMapper logMapper;
    private final Map<String, TaskHandler> handlers = new LinkedHashMap<>();
    private final Map<Long, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    public SchedulerRegistry(
            @Qualifier(SchedulerConfig.SCHEDULER_BEAN) ThreadPoolTaskScheduler scheduler,
            SysSchedulerTaskRegistryMapper registryMapper,
            SysSchedulerTaskLogMapper logMapper,
            List<TaskHandler> handlerList) {
        this.scheduler = scheduler;
        this.registryMapper = registryMapper;
        this.logMapper = logMapper;
        handlerList.forEach(h -> handlers.put(h.key(), h));
    }

    /** 服务启动时装载；装载失败只告警，不让调度表拖垮整个平台服务。 */
    @PostConstruct
    public void init() {
        reload();
    }

    /** 注册项增删改/启停后重新装载全部启用任务。 */
    public synchronized void reload() {
        scheduled.values().forEach(f -> f.cancel(true));
        scheduled.clear();
        try {
            List<SysSchedulerTaskRegistry> all = registryMapper.selectEnabledAcrossTenants();
            all.forEach(this::register);
            log.info("调度任务装载完成: 启用 {} 项, 已注册 {} 项", all.size(), scheduled.size());
        } catch (Exception e) {
            log.warn("调度任务装载失败（不影响服务启动）: {}", e.getMessage());
        }
    }

    private void register(SysSchedulerTaskRegistry t) {
        try {
            // 先校验 cron，再用 CronTrigger 注册：Spring 7 的 TaskScheduler
            // 没有 schedule(Runnable, CronExpression) 重载，只有 Trigger/Instant。
            CronExpression.parse(t.getCron());
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> execute(t, "CRON", 0), new CronTrigger(t.getCron()));
            if (future != null) {
                scheduled.put(t.getId(), future);
            }
        } catch (IllegalArgumentException e) {
            log.warn("调度任务 cron 非法, 跳过注册: key={}, cron={}", t.getTaskKey(), t.getCron());
        }
    }

    /** 运行时是否已装载（前端看板据此显示「已生效」）。 */
    public boolean isScheduled(Long taskId) {
        return scheduled.containsKey(taskId);
    }

    /** 可用处理器列表。 */
    public Set<String> handlerKeys() {
        return handlers.keySet();
    }

    /** 处理器说明（看板下拉用，缺省回退为 key 本身）。 */
    public String handlerDescription(String key) {
        TaskHandler h = handlers.get(key);
        return h == null ? key : h.description();
    }

    /** 手动立即执行：同步执行并返回结果，便于前端直接看到成败。 */
    public RunResponse runNow(SysSchedulerTaskRegistry t) {
        return execute(t, "MANUAL", 0);
    }

    private RunResponse execute(SysSchedulerTaskRegistry t, String trigger, int retryNo) {
        long start = System.currentTimeMillis();
        String status = "FAILED";
        String message = "";
        boolean retryable = true;

        // 调度线程没有请求上下文：显式重建租户，业务 SQL 才不会落到 tenant_id=0
        TraceContext.setTenantId(t.getTenantId());
        TraceContext.setTraceId("sched-" + t.getId() + "-" + System.currentTimeMillis());
        TraceContext.setUserId(0L);
        try {
            TaskHandler handler = handlers.get(t.getHandler());
            if (handler == null) {
                retryable = false;
                message = "未找到处理器: " + t.getHandler() + "（可用: " + handlers.keySet() + "）";
            } else {
                message = handler.execute();
                status = "SUCCESS";
            }
        } catch (Exception e) {
            message = "执行异常: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("调度任务执行失败: key={}, retry={}, reason={}", t.getTaskKey(), retryNo, message);
        } finally {
            writeLog(t, trigger, status, retryNo, System.currentTimeMillis() - start, message);
            TraceContext.clear();
        }

        if ("FAILED".equals(status) && retryable && retryNo < MAX_RETRY) {
            int next = retryNo + 1;
            long delay = 5L * next * next;
            scheduler.schedule(() -> execute(t, "RETRY", next), Instant.now().plusSeconds(delay));
        }
        return new RunResponse(t.getTaskKey(), status, trim(message));
    }

    private void writeLog(SysSchedulerTaskRegistry t, String trigger, String status,
                          int retryNo, long durationMs, String message) {
        try {
            SysSchedulerTaskLog row = new SysSchedulerTaskLog();
            row.setTaskId(t.getId());
            row.setTaskKey(t.getTaskKey());
            row.setHandler(t.getHandler());
            row.setTriggerType(trigger);
            row.setStatus(status);
            row.setRetryNo(retryNo);
            row.setDurationMs(durationMs);
            row.setMessage(trim(message));
            logMapper.insert(row);
        } catch (Exception e) {
            // 日志写失败不能掩盖任务本身的失败，但必须留下痕迹
            log.error("调度执行日志写入失败: key={}, reason={}", t.getTaskKey(), e.getMessage());
        }
    }

    private static String trim(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MESSAGE_LIMIT ? text : text.substring(0, MESSAGE_LIMIT);
    }
}

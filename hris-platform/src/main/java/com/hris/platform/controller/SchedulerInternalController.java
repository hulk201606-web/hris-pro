package com.hris.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.api.R;
import com.hris.common.trace.TraceContext;
import com.hris.platform.entity.SysSchedulerTaskRegistry;
import com.hris.platform.mapper.SysSchedulerTaskRegistryMapper;
import com.hris.platform.scheduler.SchedulerRegistry;
import com.hris.platform.scheduler.TaskHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 调度中心内部端点（网关封禁外部访问）。
 *
 * <p>新租户开通后的默认任务注册（docs/19 §10.5-A2）：审批落账与报表导出依赖
 * sys_scheduler_task_registry 注册项，此前仅手工 SQL seed（demo 租户）——新租户
 * 开通后两个任务缺失，审批落账/导出立即断裂。本端点幂等注册缺省任务，供开通
 * 流水线/运维脚本调用。</p>
 */
@RestController
@RequestMapping("/internal/plt/scheduler")
public class SchedulerInternalController {

    /** 新租户缺省任务：handler 必须在 TaskHandler 白名单内，cron 与既有约定一致。 */
    private static final List<DefaultTask> DEFAULT_TASKS = List.of(
            new DefaultTask("EXPORT_TASK_DISPATCH", "0 */10 * * * *"),
            new DefaultTask("PENDING_TRANSACTION_APPLY", "0 0 3 * * *"));

    private record DefaultTask(String taskKey, String cron) {
    }

    private final SysSchedulerTaskRegistryMapper taskMapper;
    private final SchedulerRegistry registry;
    private final List<TaskHandler> handlers;

    public SchedulerInternalController(SysSchedulerTaskRegistryMapper taskMapper,
                                       SchedulerRegistry registry, List<TaskHandler> handlers) {
        this.taskMapper = taskMapper;
        this.registry = registry;
        this.handlers = handlers;
    }

    /**
     * 幂等注册缺省调度任务（同租户同 task_key 已存在则跳过），随后 reload 生效。
     * 显式 setTenantId（内部调用以参数为准，租户插件自动注入行租户）+ finally 清理。
     */
    @PostMapping("/register-defaults")
    public R<Map<String, Integer>> registerDefaults(@RequestParam Long tenantId) {
        List<String> handlerKeys = handlers.stream().map(TaskHandler::key).toList();
        TraceContext.setTenantId(tenantId);
        try {
            int created = 0;
            int skipped = 0;
            for (DefaultTask t : DEFAULT_TASKS) {
                if (!handlerKeys.contains(t.taskKey())) {
                    throw new IllegalStateException("处理器不存在: " + t.taskKey());
                }
                Long exists = taskMapper.selectCount(new LambdaQueryWrapper<SysSchedulerTaskRegistry>()
                        .eq(SysSchedulerTaskRegistry::getTaskKey, t.taskKey()));
                if (exists > 0) {
                    skipped++;
                    continue;
                }
                SysSchedulerTaskRegistry e = new SysSchedulerTaskRegistry();
                e.setTaskKey(t.taskKey());
                e.setCron(t.cron());
                e.setHandler(t.taskKey());
                e.setStatus(1);
                taskMapper.insert(e);
                created++;
            }
            registry.reload();
            return R.ok(Map.of("created", created, "skipped", skipped));
        } finally {
            TraceContext.setTenantId(null);
        }
    }
}

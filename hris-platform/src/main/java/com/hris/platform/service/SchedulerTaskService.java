package com.hris.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.PageResult;
import com.hris.platform.dto.SchedulerDtos.HandlerOption;
import com.hris.platform.dto.SchedulerDtos.RunResponse;
import com.hris.platform.dto.SchedulerDtos.TaskLogResponse;
import com.hris.platform.dto.SchedulerDtos.TaskRequest;
import com.hris.platform.dto.SchedulerDtos.TaskResponse;
import com.hris.platform.entity.SysSchedulerTaskLog;
import com.hris.platform.entity.SysSchedulerTaskRegistry;
import com.hris.platform.mapper.SysSchedulerTaskLogMapper;
import com.hris.platform.mapper.SysSchedulerTaskRegistryMapper;
import com.hris.platform.scheduler.SchedulerRegistry;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 调度任务看板服务（PLT-F-011）。
 *
 * <p>刻意不提供删除端点，只提供启用/停用：注册表唯一键是
 * (tenant_id, task_key)，而项目全局用逻辑删除——删掉后再建同 key 会触发
 * 1062 唯一键冲突（节假日历上已实测过这个坑）。停用能达成同样目的，
 * 且保留 task_key 与历史执行日志的对应关系。</p>
 */
@Service
public class SchedulerTaskService {

    private final SysSchedulerTaskRegistryMapper taskMapper;
    private final SysSchedulerTaskLogMapper logMapper;
    private final SchedulerRegistry registry;

    public SchedulerTaskService(SysSchedulerTaskRegistryMapper taskMapper,
                                SysSchedulerTaskLogMapper logMapper,
                                SchedulerRegistry registry) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.registry = registry;
    }

    public List<TaskResponse> list() {
        LambdaQueryWrapper<SysSchedulerTaskRegistry> qw = new LambdaQueryWrapper<>();
        qw.orderByAsc(SysSchedulerTaskRegistry::getTaskKey);
        return taskMapper.selectList(qw).stream().map(this::toResponse).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskResponse create(TaskRequest req) {
        validate(req);
        requireKeyFree(req.taskKey(), null);
        SysSchedulerTaskRegistry e = new SysSchedulerTaskRegistry();
        apply(e, req);
        taskMapper.insert(e);
        registry.reload();
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskResponse update(Long id, TaskRequest req) {
        validate(req);
        SysSchedulerTaskRegistry e = require(id);
        requireKeyFree(req.taskKey(), id);
        apply(e, req);
        taskMapper.updateById(e);
        registry.reload();
        return toResponse(e);
    }

    /** 立即执行：不经 cron，同步返回本次结果（失败重试仍由调度器负责）。 */
    public RunResponse runNow(Long id) {
        return registry.runNow(require(id));
    }

    public PageResult<TaskLogResponse> logs(String taskKey, long page, long size) {
        LambdaQueryWrapper<SysSchedulerTaskLog> qw = new LambdaQueryWrapper<>();
        if (taskKey != null && !taskKey.isBlank()) {
            qw.eq(SysSchedulerTaskLog::getTaskKey, taskKey.trim());
        }
        qw.orderByDesc(SysSchedulerTaskLog::getCreatedAt);
        Page<SysSchedulerTaskLog> p = logMapper.selectPage(new Page<>(page, size), qw);
        List<TaskLogResponse> records = p.getRecords().stream().map(this::toLogResponse).toList();
        return new PageResult<>(records, p.getCurrent(), p.getSize(), p.getTotal());
    }

    /** 可选处理器：注册时只能从这份白名单里选，避免配出不存在的 handler。 */
    public List<HandlerOption> handlers() {
        return registry.handlerKeys().stream()
                .map(k -> new HandlerOption(k, descriptionOf(k)))
                .toList();
    }

    private String descriptionOf(String key) {
        // 处理器说明由注册表提供；这里不重复维护一份文案，避免漂移
        return registry.handlerDescription(key);
    }

    private void validate(TaskRequest req) {
        try {
            CronExpression.parse(req.cron());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "cron 表达式非法: " + req.cron());
        }
        if (!registry.handlerKeys().contains(req.handler())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "处理器不存在: " + req.handler() + "（可用: " + registry.handlerKeys() + "）");
        }
    }

    private void requireKeyFree(String taskKey, Long excludeId) {
        LambdaQueryWrapper<SysSchedulerTaskRegistry> qw = new LambdaQueryWrapper<>();
        qw.eq(SysSchedulerTaskRegistry::getTaskKey, taskKey);
        if (excludeId != null) {
            qw.ne(SysSchedulerTaskRegistry::getId, excludeId);
        }
        if (taskMapper.selectCount(qw) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务键已存在: " + taskKey);
        }
    }

    private void apply(SysSchedulerTaskRegistry e, TaskRequest req) {
        e.setTaskKey(req.taskKey());
        e.setCron(req.cron());
        e.setHandler(req.handler());
        e.setStatus(req.status() == null || req.status() != 0 ? 1 : 0);
    }

    private SysSchedulerTaskRegistry require(Long id) {
        SysSchedulerTaskRegistry e = taskMapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "调度任务不存在");
        }
        return e;
    }

    private TaskResponse toResponse(SysSchedulerTaskRegistry e) {
        return new TaskResponse(e.getId(), e.getTaskKey(), e.getCron(), e.getHandler(),
                e.getStatus(), registry.isScheduled(e.getId()), e.getUpdatedAt());
    }

    private TaskLogResponse toLogResponse(SysSchedulerTaskLog e) {
        return new TaskLogResponse(e.getId(), e.getTaskId(), e.getTaskKey(), e.getHandler(),
                e.getTriggerType(), e.getStatus(), e.getRetryNo(), e.getDurationMs(),
                e.getMessage(), e.getCreatedAt());
    }
}

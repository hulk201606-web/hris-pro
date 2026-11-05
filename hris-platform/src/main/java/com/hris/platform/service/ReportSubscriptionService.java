package com.hris.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.ReportDtos.ExportTaskRequest;
import com.hris.platform.dto.ReportDtos.ExportTaskResponse;
import com.hris.platform.dto.ReportSubscriptionDtos.SubscriptionRequest;
import com.hris.platform.dto.ReportSubscriptionDtos.SubscriptionResponse;
import com.hris.platform.entity.SysReportSubscription;
import com.hris.platform.entity.SysSchedulerTaskRegistry;
import com.hris.platform.mapper.SysReportDefMapper;
import com.hris.platform.mapper.SysReportSubscriptionMapper;
import com.hris.platform.mapper.SysSchedulerTaskRegistryMapper;
import com.hris.platform.scheduler.ReportSubscriptionDispatchHandler;
import com.hris.platform.scheduler.SchedulerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 报表订阅（PLT-F-005）：CRUD + 调度注册 + 触发导出 + 通知发送。
 *
 * <p>复用调度中心范式：每条启用订阅对应 sys_scheduler_task_registry 的一行，
 * task_key={@code RPT_SUB_<id>}、handler={@code REPORT_SUBSCRIPTION_DISPATCH}；
 * 增改后 {@link SchedulerRegistry#reload()} 动态生效，停用/删除只置任务 status=0
 * （不物理删，避免逻辑删除后同 key 再建撞唯一键）。</p>
 *
 * <p>执行语义（MVP）：handler 触发时先创建导出任务（PENDING，由导出执行器异步落文件），
 * 紧接着按渠道/接收人发送通知。真实「导出完成后再发」需等文件回执，当前以排队通知
 * 保证链路闭环，文案明确标注任务号。</p>
 */
@Service
public class ReportSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(ReportSubscriptionService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> CHANNELS = Set.of("EMAIL", "WECOM", "DINGTALK");
    private static final String TASK_PREFIX = "RPT_SUB_";

    private final SysReportSubscriptionMapper mapper;
    private final SysReportDefMapper reportDefMapper;
    private final SysSchedulerTaskRegistryMapper taskMapper;
    private final ObjectProvider<SchedulerRegistry> registryProvider;
    private final ExportTaskService exportTaskService;
    private final InternalNotifyService notifyService;

    public ReportSubscriptionService(SysReportSubscriptionMapper mapper,
                                     SysReportDefMapper reportDefMapper,
                                     SysSchedulerTaskRegistryMapper taskMapper,
                                     ObjectProvider<SchedulerRegistry> registryProvider,
                                     ExportTaskService exportTaskService,
                                     InternalNotifyService notifyService) {
        this.mapper = mapper;
        this.reportDefMapper = reportDefMapper;
        this.taskMapper = taskMapper;
        this.registryProvider = registryProvider;
        this.exportTaskService = exportTaskService;
        this.notifyService = notifyService;
    }

    /** 订阅列表。 */
    public List<SubscriptionResponse> list() {
        return mapper.selectList(new LambdaQueryWrapper<SysReportSubscription>()
                        .orderByDesc(SysReportSubscription::getId))
                .stream().map(this::toResponse).toList();
    }

    /** 新建订阅并注册调度任务。 */
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionResponse create(SubscriptionRequest req) {
        validate(req);
        SysReportSubscription row = new SysReportSubscription();
        apply(row, req);
        mapper.insert(row);
        syncTask(row);
        return toResponse(row);
    }

    /** 更新订阅并同步调度任务（停用即下线）。 */
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionResponse update(Long id, SubscriptionRequest req) {
        validate(req);
        SysReportSubscription row = require(id);
        apply(row, req);
        mapper.updateById(row);
        syncTask(row);
        return toResponse(row);
    }

    /** 删除订阅（逻辑删除）并下线调度任务。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysReportSubscription row = require(id);
        row.setStatus(0);
        mapper.updateById(row);
        mapper.deleteById(id);
        syncTask(row);
    }

    /**
     * 触发一次订阅执行：创建导出任务 + 发送通知。返回执行摘要（写调度日志）。
     * 不抛异常给调度器：重复触发会产生重复导出任务，重试语义不适用。
     */
    public String execute(Long subscriptionId) {
        SysReportSubscription row = mapper.selectById(subscriptionId);
        if (row == null) {
            return "订阅不存在: " + subscriptionId;
        }
        if (row.getStatus() == null || row.getStatus() != 1) {
            return "订阅已停用: " + subscriptionId;
        }
        ExportTaskResponse task = exportTaskService.create(new ExportTaskRequest(row.getReportDefId()));
        String subject = "报表订阅已触发导出";
        String content = "订阅 #" + row.getId() + " 已生成导出任务 #" + task.id()
                + "（报表定义 #" + row.getReportDefId() + "），导出完成后请到报表中心下载。";
        int sent = 0;
        int failed = 0;
        for (String channel : parse(row.getChannels())) {
            for (String recipient : parse(row.getRecipients())) {
                var resp = notifyService.notify(new com.hris.platform.dto.InternalNotifyDtos.NotifyRequest(
                        channel, recipient, subject, content, "REPORT_SUBSCRIPTION", "sub:" + row.getId()));
                if (resp.success()) {
                    sent++;
                } else {
                    failed++;
                }
            }
        }
        String summary = String.format("导出任务 #%d 已创建, 通知成功 %d, 失败 %d", task.id(), sent, failed);
        log.info("报表订阅执行: subId={}, {}", row.getId(), summary);
        return summary;
    }

    /** 供 handler 解析 task_key。 */
    public static Long subscriptionId(String taskKey) {
        if (taskKey == null || !taskKey.startsWith(TASK_PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(taskKey.substring(TASK_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void syncTask(SysReportSubscription row) {
        String taskKey = TASK_PREFIX + row.getId();
        int status = row.getStatus() != null && row.getStatus() == 1 ? 1 : 0;
        SysSchedulerTaskRegistry task = taskMapper.selectOne(new LambdaQueryWrapper<SysSchedulerTaskRegistry>()
                .eq(SysSchedulerTaskRegistry::getTaskKey, taskKey));
        if (task == null) {
            task = new SysSchedulerTaskRegistry();
            task.setTaskKey(taskKey);
            task.setHandler(ReportSubscriptionDispatchHandler.KEY);
            task.setCron(row.getCronExpr());
            task.setStatus(status);
            taskMapper.insert(task);
        } else {
            task.setCron(row.getCronExpr());
            task.setHandler(ReportSubscriptionDispatchHandler.KEY);
            task.setStatus(status);
            taskMapper.updateById(task);
        }
        SchedulerRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            registry.reload();
        }
    }

    private void validate(SubscriptionRequest req) {
        if (req == null || req.reportDefId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "reportDefId 不能为空");
        }
        if (reportDefMapper.selectById(req.reportDefId()) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "报表定义不存在");
        }
        try {
            CronExpression.parse(req.cronExpr());
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "cron 表达式非法: " + req.cronExpr());
        }
        List<String> channels = req.channels() == null ? List.of() : req.channels();
        if (channels.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "至少选择一个通知渠道");
        }
        for (String c : channels) {
            if (c == null || !CHANNELS.contains(c.trim().toUpperCase(Locale.ROOT))) {
                throw new BizException(ErrorCode.PARAM_INVALID, "不支持的渠道: " + c);
            }
        }
        if (req.recipients() == null || req.recipients().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接收人不能为空");
        }
    }

    private void apply(SysReportSubscription row, SubscriptionRequest req) {
        row.setReportDefId(req.reportDefId());
        row.setCronExpr(req.cronExpr().trim());
        row.setChannels(JSON.writeValueAsString(req.channels().stream()
                .map(c -> c.trim().toUpperCase(Locale.ROOT)).toList()));
        row.setRecipients(JSON.writeValueAsString(req.recipients()));
        row.setStatus(req.status() == null || req.status() != 0 ? 1 : 0);
    }

    private SysReportSubscription require(Long id) {
        SysReportSubscription row = mapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "报表订阅不存在");
        }
        return row;
    }

    private List<String> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.asList(JSON.readValue(json, String[].class));
        } catch (Exception e) {
            log.warn("报表订阅 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    private SubscriptionResponse toResponse(SysReportSubscription row) {
        String taskKey = TASK_PREFIX + row.getId();
        SchedulerRegistry registry = registryProvider.getIfAvailable();
        boolean scheduled = registry != null && row.getId() != null && isScheduled(registry, row.getId());
        return new SubscriptionResponse(row.getId(), row.getReportDefId(), row.getCronExpr(),
                parse(row.getChannels()), parse(row.getRecipients()), row.getStatus(),
                taskKey, scheduled, row.getUpdatedAt());
    }

    private boolean isScheduled(SchedulerRegistry registry, Long subscriptionId) {
        SysSchedulerTaskRegistry task = taskMapper.selectOne(new LambdaQueryWrapper<SysSchedulerTaskRegistry>()
                .eq(SysSchedulerTaskRegistry::getTaskKey, TASK_PREFIX + subscriptionId));
        return task != null && registry.isScheduled(task.getId());
    }
}

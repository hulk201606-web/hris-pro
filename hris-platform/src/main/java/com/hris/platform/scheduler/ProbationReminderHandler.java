package com.hris.platform.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.trace.TraceContext;
import com.hris.platform.client.EmployeeOpsClient;
import com.hris.platform.dto.ProbationReminderItem;
import com.hris.platform.entity.SysNotifyRecord;
import com.hris.platform.entity.SysNotifyTemplate;
import com.hris.platform.mapper.SysNotifyRecordMapper;
import com.hris.platform.mapper.SysNotifyTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 试用期到期提醒调度任务（EMP-F-007 深化）。
 *
 * <p>转正前 15/7/3 天三档提醒：跨域 Feign 调 hris-employee 的
 * {@code /internal/emp/probation-reminders?windowDays=15}，对命中 15/7/3 档的
 * 员工写入通知记录。无匹配模板（sys_notify_template 无 PROBATION_REMINDER）则落
 * 基础通知记录，不接发送通道——发送由 PLT 通知通道深化时统一接管。</p>
 *
 * <p><b>幂等去重</b>：同员工同档不重复落。sys_notify_record 无业务键列，MVP 以
 * channel 承载档位（PROBATION_D15/D7/D3）、user_id 承载员工域 empId（系统生成的
 * 通知无 auth_user 映射，channel 语义随场景扩展，见通知模块深化排期），
 * 按 (channel, user_id) 存在性判定去重。</p>
 */
@Component
public class ProbationReminderHandler implements TaskHandler {

    /** 与 sys_scheduler_task_registry.handler 对应的标识。 */
    public static final String KEY = "PROBATION_REMINDER";

    /** 通知模板编码（无模板时落基础记录）。 */
    public static final String TEMPLATE_CODE = "PROBATION_REMINDER";

    static final String CHANNEL_PREFIX = "PROBATION_";

    private static final Logger log = LoggerFactory.getLogger(ProbationReminderHandler.class);
    private static final int WINDOW_DAYS = 15;

    private final EmployeeOpsClient employeeOpsClient;
    private final SysNotifyRecordMapper notifyRecordMapper;
    private final SysNotifyTemplateMapper notifyTemplateMapper;

    public ProbationReminderHandler(EmployeeOpsClient employeeOpsClient,
                                    SysNotifyRecordMapper notifyRecordMapper,
                                    SysNotifyTemplateMapper notifyTemplateMapper) {
        this.employeeOpsClient = employeeOpsClient;
        this.notifyRecordMapper = notifyRecordMapper;
        this.notifyTemplateMapper = notifyTemplateMapper;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String description() {
        return "试用期到期提醒：扫描 15 天窗口内试用期到期合同，按 15/7/3 天三档写通知记录";
    }

    @Override
    public String execute() throws Exception {
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("调度线程缺少租户上下文，拒绝执行（防跨租户误发提醒）");
        }
        var resp = employeeOpsClient.probationReminders(WINDOW_DAYS);
        if (resp != null && resp.getCode() != 0) {
            throw new IllegalStateException("员工域试用期提醒查询返回失败: code=" + resp.getCode()
                    + " msg=" + resp.getMessage());
        }
        List<ProbationReminderItem> items = resp == null || resp.getData() == null
                ? List.of() : resp.getData();
        if (items.isEmpty()) {
            return "窗口内无试用期到期员工";
        }

        SysNotifyTemplate template = findTemplate();
        int written = 0;
        int skipped = 0;
        for (ProbationReminderItem item : items) {
            String channel = CHANNEL_PREFIX + item.level();
            if (alreadyNotified(channel, item.empId())) {
                skipped++;
                continue;
            }
            writeRecord(item, channel, template);
            written++;
        }
        log.info("试用期提醒完成: tenant={}, 命中={}, 落记录={}, 跳过(已通知)={}",
                tenantId, items.size(), written, skipped);
        return "命中 " + items.size() + " 条，落通知 " + written + " 条，跳过 " + skipped + " 条";
    }

    /** 按编码查模板（无模板返回 null，落基础通知记录——不阻断提醒）。 */
    private SysNotifyTemplate findTemplate() {
        return notifyTemplateMapper.selectOne(new LambdaQueryWrapper<SysNotifyTemplate>()
                .eq(SysNotifyTemplate::getCode, TEMPLATE_CODE)
                .last("LIMIT 1"));
    }

    /** 去重：同员工同档已存在通知记录则跳过（不重复落）。 */
    private boolean alreadyNotified(String channel, Long empId) {
        Long cnt = notifyRecordMapper.selectCount(new LambdaQueryWrapper<SysNotifyRecord>()
                .eq(SysNotifyRecord::getChannel, channel)
                .eq(SysNotifyRecord::getUserId, empId));
        return cnt != null && cnt > 0;
    }

    private void writeRecord(ProbationReminderItem item, String channel, SysNotifyTemplate template) {
        SysNotifyRecord r = new SysNotifyRecord();
        r.setUserId(item.empId());
        r.setTemplateId(template == null ? null : template.getId());
        r.setChannel(channel);
        r.setSendStatus("SENT");
        r.setSentAt(LocalDateTime.now());
        notifyRecordMapper.insert(r);
    }
}

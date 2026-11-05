package com.hris.platform.service;

import com.hris.common.exception.BizException;
import com.hris.platform.dto.ReportDtos.ExportTaskResponse;
import com.hris.platform.dto.ReportSubscriptionDtos.SubscriptionRequest;
import com.hris.platform.entity.SysReportDef;
import com.hris.platform.entity.SysReportSubscription;
import com.hris.platform.entity.SysSchedulerTaskRegistry;
import com.hris.platform.mapper.SysReportDefMapper;
import com.hris.platform.mapper.SysReportSubscriptionMapper;
import com.hris.platform.mapper.SysSchedulerTaskRegistryMapper;
import com.hris.platform.scheduler.SchedulerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报表订阅（PLT-F-005）：CRUD / 调度注册 / 触发导出 + 通知串联。
 */
@ExtendWith(MockitoExtension.class)
class ReportSubscriptionServiceTest {

    @Mock
    private SysReportSubscriptionMapper mapper;
    @Mock
    private SysReportDefMapper reportDefMapper;
    @Mock
    private SysSchedulerTaskRegistryMapper taskMapper;
    @Mock
    private ObjectProvider<SchedulerRegistry> registryProvider;
    @Mock
    private ExportTaskService exportTaskService;
    @Mock
    private InternalNotifyService notifyService;

    private ReportSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new ReportSubscriptionService(mapper, reportDefMapper, taskMapper,
                registryProvider, exportTaskService, notifyService);
    }

    private SubscriptionRequest req() {
        return new SubscriptionRequest(7L, "0 0 8 * * *", List.of("EMAIL", "WECOM"),
                List.of("hr@x.com"), 1);
    }

    private void stubReportDef() {
        SysReportDef def = new SysReportDef();
        def.setId(7L);
        when(reportDefMapper.selectById(7L)).thenReturn(def);
    }

    @Test
    @DisplayName("新建订阅 → 落库并注册 RPT_SUB_{id} 调度任务")
    void createRegistersSchedulerTask() {
        stubReportDef();
        doAnswer(inv -> {
            ((SysReportSubscription) inv.getArgument(0)).setId(10L);
            return 1;
        }).when(mapper).insert(any(SysReportSubscription.class));
        when(taskMapper.selectOne(any())).thenReturn(null);

        var resp = service.create(req());

        assertEquals(10L, resp.id());
        assertEquals("RPT_SUB_10", resp.schedulerTaskKey());
        ArgumentCaptor<SysSchedulerTaskRegistry> task = ArgumentCaptor.forClass(SysSchedulerTaskRegistry.class);
        verify(taskMapper).insert(task.capture());
        assertEquals("RPT_SUB_10", task.getValue().getTaskKey());
        assertEquals("REPORT_SUBSCRIPTION_DISPATCH", task.getValue().getHandler());
        assertEquals("0 0 8 * * *", task.getValue().getCron());
        assertEquals(1, task.getValue().getStatus());
    }

    @Test
    @DisplayName("停用订阅 → 调度任务 status=0（不物理删，避免唯一键冲突）")
    void disableTurnsOffTask() {
        SysReportSubscription row = new SysReportSubscription();
        row.setId(10L);
        row.setReportDefId(7L);
        row.setCronExpr("0 0 8 * * *");
        row.setChannels("[\"EMAIL\"]");
        row.setRecipients("[\"hr@x.com\"]");
        row.setStatus(1);
        when(mapper.selectById(10L)).thenReturn(row);
        SysSchedulerTaskRegistry task = new SysSchedulerTaskRegistry();
        task.setId(99L);
        task.setTaskKey("RPT_SUB_10");
        when(taskMapper.selectOne(any())).thenReturn(task);

        service.delete(10L);

        assertEquals(0, task.getStatus());
        verify(taskMapper).updateById(task);
        verify(mapper).deleteById(10L);
    }

    @Test
    @DisplayName("cron 非法 → PARAM_INVALID，不落库")
    void rejectsInvalidCron() {
        stubReportDef();
        SubscriptionRequest bad = new SubscriptionRequest(7L, "not-a-cron", List.of("EMAIL"),
                List.of("hr@x.com"), 1);

        BizException ex = assertThrows(BizException.class, () -> service.create(bad));

        assertEquals(10001, ex.getCode());
        verify(mapper, never()).insert(any(SysReportSubscription.class));
    }

    @Test
    @DisplayName("渠道不支持 → PARAM_INVALID")
    void rejectsUnknownChannel() {
        stubReportDef();
        SubscriptionRequest bad = new SubscriptionRequest(7L, "0 0 8 * * *", List.of("SMS"),
                List.of("hr@x.com"), 1);

        BizException ex = assertThrows(BizException.class, () -> service.create(bad));

        assertEquals(10001, ex.getCode());
    }

    @Test
    @DisplayName("执行订阅 → 创建导出任务并按渠道/接收人发送通知")
    void executeCreatesExportAndNotifies() {
        SysReportSubscription row = new SysReportSubscription();
        row.setId(10L);
        row.setReportDefId(7L);
        row.setChannels("[\"EMAIL\",\"WECOM\"]");
        row.setRecipients("[\"hr@x.com\",\"boss@x.com\"]");
        row.setStatus(1);
        when(mapper.selectById(10L)).thenReturn(row);
        when(exportTaskService.create(any()))
                .thenReturn(new ExportTaskResponse(77L, 7L, "PENDING", null, LocalDateTime.now()));
        when(notifyService.notify(any()))
                .thenReturn(new com.hris.platform.dto.InternalNotifyDtos.NotifyResponse(
                        true, "SENT", "EMAIL", "已发送", null, 200));

        String summary = service.execute(10L);

        assertTrue(summary.contains("77"), "实际=" + summary);
        assertTrue(summary.contains("通知成功 4"), "实际=" + summary);
        verify(exportTaskService).create(any());
        verify(notifyService, org.mockito.Mockito.times(4)).notify(any());
    }

    @Test
    @DisplayName("订阅停用 → 执行直接跳过，不产生导出任务")
    void executeSkipsDisabled() {
        SysReportSubscription row = new SysReportSubscription();
        row.setId(10L);
        row.setStatus(0);
        when(mapper.selectById(10L)).thenReturn(row);

        assertTrue(service.execute(10L).contains("已停用"));
        verify(exportTaskService, never()).create(any());
    }
}

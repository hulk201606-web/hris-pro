package com.hris.platform.scheduler;

import com.hris.common.api.R;
import com.hris.platform.client.EmployeeOpsClient;
import com.hris.platform.dto.ProbationReminderItem;
import com.hris.platform.entity.SysNotifyRecord;
import com.hris.platform.entity.SysNotifyTemplate;
import com.hris.platform.mapper.SysNotifyRecordMapper;
import com.hris.platform.mapper.SysNotifyTemplateMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProbationReminderHandler 单元测试（EMP-F-007 深化）。
 *
 * <p>核心是 15/7/3 三档归档与幂等去重：同员工同档只落一条通知记录，
 * sys_notify_record 无业务键列，以 (channel=PROBATION_Dxx, user_id=empId) 判重。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProbationReminderHandlerTest {

    @Mock
    private EmployeeOpsClient employeeOpsClient;
    @Mock
    private SysNotifyRecordMapper notifyRecordMapper;
    @Mock
    private SysNotifyTemplateMapper notifyTemplateMapper;

    private ProbationReminderHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ProbationReminderHandler(employeeOpsClient, notifyRecordMapper, notifyTemplateMapper);
        com.hris.common.trace.TraceContext.setTenantId(1L);
        com.hris.common.trace.TraceContext.setUserId(0L);
    }

    @AfterEach
    void tearDown() {
        com.hris.common.trace.TraceContext.clear();
    }

    private void stubReminders(ProbationReminderItem... items) {
        when(employeeOpsClient.probationReminders(15))
                .thenReturn(R.ok(List.of(items)));
        when(notifyRecordMapper.selectCount(any())).thenReturn(0L);
        when(notifyTemplateMapper.selectOne(any())).thenReturn(null);
    }

    private ProbationReminderItem item(long days) {
        return new ProbationReminderItem(1L, 100L, "E001", "张三", 7L,
                LocalDate.now().plusDays(days), days, ProbationReminderService_Level.level(days));
    }

    /** 复用 emp 侧归档口径（跨服务契约一致性的最小镜像）。 */
    static final class ProbationReminderService_Level {
        static String level(long days) {
            if (days <= 3) {
                return "D3";
            }
            return days <= 7 ? "D7" : "D15";
        }
    }

    @Nested
    @DisplayName("通知写入与去重")
    class Notify {

        @Test
        @DisplayName("命中 15/7/3 三档 → 各落一条记录，channel 承载档位")
        void writesPerLevel() throws Exception {
            stubReminders(item(15), item(7), item(3));

            String summary = handler.execute();

            ArgumentCaptor<SysNotifyRecord> captor = ArgumentCaptor.forClass(SysNotifyRecord.class);
            verify(notifyRecordMapper, times(3)).insert(captor.capture());
            List<String> channels = captor.getAllValues().stream().map(SysNotifyRecord::getChannel).toList();
            assertTrue(channels.contains("PROBATION_D15"));
            assertTrue(channels.contains("PROBATION_D7"));
            assertTrue(channels.contains("PROBATION_D3"));
            assertTrue(summary.contains("落通知 3 条"), "summary=" + summary);
        }

        @Test
        @DisplayName("同员工同档已落过 → 跳过不重复写")
        void dedupesSameEmpSameLevel() throws Exception {
            stubReminders(item(5));
            when(notifyRecordMapper.selectCount(any())).thenReturn(1L);

            String summary = handler.execute();

            verify(notifyRecordMapper, never()).insert(any(SysNotifyRecord.class));
            assertTrue(summary.contains("跳过 1 条"), "summary=" + summary);
        }

        @Test
        @DisplayName("窗口内无到期员工 → 不写任何记录")
        void emptyWindowWritesNothing() throws Exception {
            when(employeeOpsClient.probationReminders(15)).thenReturn(R.ok(List.of()));

            String summary = handler.execute();

            verify(notifyRecordMapper, never()).insert(any(SysNotifyRecord.class));
            assertTrue(summary.contains("无试用期到期"));
        }

        @Test
        @DisplayName("无匹配模板 → templateId 为空仍落基础记录（不阻断）")
        void writesBasicRecordWithoutTemplate() throws Exception {
            stubReminders(item(10));

            handler.execute();

            ArgumentCaptor<SysNotifyRecord> captor = ArgumentCaptor.forClass(SysNotifyRecord.class);
            verify(notifyRecordMapper).insert(captor.capture());
            assertEquals(null, captor.getValue().getTemplateId());
            assertEquals("SENT", captor.getValue().getSendStatus());
        }

        @Test
        @DisplayName("有模板 → 关联 templateId")
        void linksTemplateWhenPresent() throws Exception {
            stubReminders(item(10));
            SysNotifyTemplate tpl = new SysNotifyTemplate();
            tpl.setId(900730002L);
            tpl.setCode("PROBATION_REMINDER");
            when(notifyTemplateMapper.selectOne(any())).thenReturn(tpl);

            handler.execute();

            ArgumentCaptor<SysNotifyRecord> captor = ArgumentCaptor.forClass(SysNotifyRecord.class);
            verify(notifyRecordMapper).insert(captor.capture());
            assertEquals(900730002L, captor.getValue().getTemplateId());
        }

        @Test
        @DisplayName("下游返回失败码 → 抛异常交由调度层记 FAILED 并重试")
        void downstreamFailureFailsTask() {
            when(employeeOpsClient.probationReminders(15)).thenReturn(R.fail(51000, "员工域错误"));

            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> handler.execute());
        }
    }
}

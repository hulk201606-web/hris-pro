package com.hris.platform.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.SchedulerDtos.TaskRequest;
import com.hris.platform.entity.SysSchedulerTaskRegistry;
import com.hris.platform.mapper.SysSchedulerTaskLogMapper;
import com.hris.platform.mapper.SysSchedulerTaskRegistryMapper;
import com.hris.platform.scheduler.SchedulerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SchedulerTaskService 单元测试（PLT-F-011 调度任务看板）。
 *
 * <p>覆盖：cron 合法性 / handler 白名单 / taskKey 唯一性守卫（新建与更新排除自身）、
 * status 默认值（null→启用，0→停用）、存在性守卫。</p>
 */
@ExtendWith(MockitoExtension.class)
class SchedulerTaskServiceTest {

    private static final String VALID_CRON = "0 0 2 * * ?";

    @Mock
    private SysSchedulerTaskRegistryMapper taskMapper;
    @Mock
    private SysSchedulerTaskLogMapper logMapper;
    @Mock
    private SchedulerRegistry registry;

    private SchedulerTaskService service;

    @BeforeEach
    void setUp() {
        service = new SchedulerTaskService(taskMapper, logMapper, registry);
    }

    private TaskRequest request(String key, String handler, Integer status) {
        return new TaskRequest(key, VALID_CRON, handler, status);
    }

    @Nested
    @DisplayName("create：cron / handler / 唯一性守卫")
    class CreateGuards {

        @Test
        @DisplayName("cron 非法 → PARAM_INVALID，不落库")
        void rejectsInvalidCron() {
            BizException e = assertThrows(BizException.class,
                    () -> service.create(new TaskRequest("k", "not-a-cron", "h", null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(taskMapper, never()).insert(any(SysSchedulerTaskRegistry.class));
        }

        @Test
        @DisplayName("handler 不在白名单 → PARAM_INVALID")
        void rejectsUnknownHandler() {
            when(registry.handlerKeys()).thenReturn(Set.of("auditPartition"));

            BizException e = assertThrows(BizException.class,
                    () -> service.create(request("k", "ghostHandler", null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(taskMapper, never()).insert(any(SysSchedulerTaskRegistry.class));
        }

        @Test
        @DisplayName("taskKey 重复 → PARAM_INVALID，不落库")
        void rejectsDuplicateKey() {
            when(registry.handlerKeys()).thenReturn(Set.of("auditPartition"));
            when(taskMapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class,
                    () -> service.create(request("dup", "auditPartition", null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(taskMapper, never()).insert(any(SysSchedulerTaskRegistry.class));
        }
    }

    @Nested
    @DisplayName("create：正常落库与 status 默认值")
    class Create {

        @Test
        @DisplayName("status 未传 → 默认启用(1)；落库后 reload 注册表")
        void defaultsStatusToEnabled() {
            when(registry.handlerKeys()).thenReturn(Set.of("auditPartition"));
            when(taskMapper.selectCount(any())).thenReturn(0L);

            service.create(request("audit", "auditPartition", null));

            ArgumentCaptor<SysSchedulerTaskRegistry> captor = ArgumentCaptor.forClass(SysSchedulerTaskRegistry.class);
            verify(taskMapper).insert(captor.capture());
            assertEquals(1, captor.getValue().getStatus(), "status 未传应默认启用(1)");
            assertEquals("audit", captor.getValue().getTaskKey());
            verify(registry).reload();
        }

        @Test
        @DisplayName("status=0 → 停用(0)")
        void keepsStatusZero() {
            when(registry.handlerKeys()).thenReturn(Set.of("auditPartition"));
            when(taskMapper.selectCount(any())).thenReturn(0L);

            service.create(request("audit", "auditPartition", 0));

            ArgumentCaptor<SysSchedulerTaskRegistry> captor = ArgumentCaptor.forClass(SysSchedulerTaskRegistry.class);
            verify(taskMapper).insert(captor.capture());
            assertEquals(0, captor.getValue().getStatus(), "status=0 应保持停用");
        }
    }

    @Nested
    @DisplayName("update：存在性与排除自身的唯一性")
    class Update {

        @Test
        @DisplayName("任务不存在 → NOT_FOUND")
        void notFound() {
            when(registry.handlerKeys()).thenReturn(Set.of("auditPartition"));
            when(taskMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.update(404L, request("audit", "auditPartition", null)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(taskMapper, never()).updateById(any(SysSchedulerTaskRegistry.class));
        }

        @Test
        @DisplayName("改到他人已占用的 taskKey → PARAM_INVALID（排除自身）")
        void rejectsKeyTakenByOther() {
            SysSchedulerTaskRegistry existing = new SysSchedulerTaskRegistry();
            existing.setId(7L);
            existing.setTaskKey("old-key");
            when(registry.handlerKeys()).thenReturn(Set.of("auditPartition"));
            when(taskMapper.selectById(7L)).thenReturn(existing);
            when(taskMapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class,
                    () -> service.update(7L, request("taken-key", "auditPartition", null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(taskMapper, never()).updateById(any(SysSchedulerTaskRegistry.class));
        }
    }

    @Nested
    @DisplayName("runNow：立即执行")
    class RunNow {

        @Test
        @DisplayName("任务不存在 → NOT_FOUND")
        void notFound() {
            when(taskMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.runNow(404L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }
    }
}

package com.hris.platform.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.ReportDtos.ExportTaskRequest;
import com.hris.platform.entity.SysExportTask;
import com.hris.platform.mapper.SysExportTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExportTaskService 单元测试（PLT-F-005 异步导出任务状态机）。
 *
 * <p>覆盖 PENDING/RUNNING/DONE/FAILED 状态机流转、创建默认 PENDING、
 * 以及「文件服务暂时不可用 → 退回 PENDING 重试」（fail-closed，不误标 DONE/FAILED）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ExportTaskServiceTest {

    @Mock
    private SysExportTaskMapper mapper;

    private ExportTaskService service;

    @BeforeEach
    void setUp() {
        service = new ExportTaskService(mapper);
    }

    private SysExportTask task() {
        SysExportTask t = new SysExportTask();
        t.setId(7L);
        t.setReportDefId(5L);
        t.setStatus("PENDING");
        return t;
    }

    @Nested
    @DisplayName("create：默认 PENDING")
    class Create {

        @Test
        @DisplayName("创建导出任务 → 状态置 PENDING、回填 reportDefId")
        void createsAsPending() {
            service.create(new ExportTaskRequest(5L));

            ArgumentCaptor<SysExportTask> captor = ArgumentCaptor.forClass(SysExportTask.class);
            verify(mapper).insert(captor.capture());
            assertEquals("PENDING", captor.getValue().getStatus(), "新建导出任务应处于 PENDING");
            assertEquals(5L, captor.getValue().getReportDefId());
        }
    }

    @Nested
    @DisplayName("markXxx：状态机流转与存在性守卫")
    class StateMachine {

        @Test
        @DisplayName("任务不存在 → NOT_FOUND，不更新")
        void notFound() {
            when(mapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.markRunning(404L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(SysExportTask.class));
        }

        @Test
        @DisplayName("markRunning → 置 RUNNING")
        void marksRunning() {
            SysExportTask t = task();
            when(mapper.selectById(7L)).thenReturn(t);

            service.markRunning(7L);

            verify(mapper).updateById(t);
            assertEquals("RUNNING", t.getStatus());
        }

        @Test
        @DisplayName("markPending（文件服务不可用）→ 退回 PENDING 重试，不判死")
        void marksPendingForRetry() {
            SysExportTask t = task();
            t.setStatus("RUNNING");
            when(mapper.selectById(7L)).thenReturn(t);

            service.markPending(7L);

            assertEquals("PENDING", t.getStatus(), "文件服务不可用应退回 PENDING 等重试");
        }

        @Test
        @DisplayName("markDone → 置 DONE 并关联 fileId")
        void marksDoneWithFile() {
            SysExportTask t = task();
            t.setStatus("RUNNING");
            when(mapper.selectById(7L)).thenReturn(t);

            service.markDone(7L, 99L);

            assertEquals("DONE", t.getStatus());
            assertEquals(99L, t.getFileId());
        }

        @Test
        @DisplayName("markFailed → 置 FAILED")
        void marksFailed() {
            SysExportTask t = task();
            t.setStatus("RUNNING");
            when(mapper.selectById(7L)).thenReturn(t);

            service.markFailed(7L);

            assertEquals("FAILED", t.getStatus());
        }
    }
}

package com.hris.flow.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.TaskExtDtos.AddAttachmentRequest;
import com.hris.flow.dto.TaskExtDtos.AttachmentView;
import com.hris.flow.entity.TaskAttachment;
import com.hris.flow.mapper.TaskAttachmentMapper;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 节点附件单测（FLW-F-020）：归属校验（仅办理人）、完成后禁传、重复挂同一文件幂等、入参校验。
 */
@ExtendWith(MockitoExtension.class)
class TaskAttachmentServiceTest {

    private static final String TASK_ID = "t1";
    private static final String PROC_INST = "pi-1";

    @Mock
    TaskAttachmentMapper attachmentMapper;
    @Mock
    TaskGuard taskGuard;

    private TaskAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new TaskAttachmentService(attachmentMapper, taskGuard);
        TraceContext.setTenantId(100001L);
        TraceContext.setUserId(5L);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    private Task writableTask() {
        Task task = mock(Task.class);
        lenient().when(task.getProcessInstanceId()).thenReturn(PROC_INST);
        lenient().when(taskGuard.requireWritableTask(TASK_ID)).thenReturn(task);
        return task;
    }

    @Test
    @DisplayName("办理人上传 → 落库并回填实例/任务/上传人")
    void shouldInsertAttachment() {
        writableTask();
        when(attachmentMapper.selectOne(any())).thenReturn(null);

        AttachmentView view = service.add(TASK_ID, new AddAttachmentRequest("1001", "审批依据.pdf"));

        ArgumentCaptor<TaskAttachment> captor = ArgumentCaptor.forClass(TaskAttachment.class);
        verify(attachmentMapper).insert(captor.capture());
        TaskAttachment row = captor.getValue();
        assertEquals(PROC_INST, row.getInstanceId());
        assertEquals(TASK_ID, row.getTaskId());
        assertEquals("1001", row.getFileId());
        assertEquals("审批依据.pdf", row.getFileName());
        assertEquals(5L, row.getUploadedBy());
        assertEquals("1001", view.fileId());
    }

    @Test
    @DisplayName("同一任务重复挂同一文件 → 幂等返回既有记录，不重复插入")
    void shouldBeIdempotentOnDuplicateFile() {
        writableTask();
        TaskAttachment existing = new TaskAttachment();
        existing.setId(9L);
        existing.setTaskId(TASK_ID);
        existing.setFileId("1001");
        when(attachmentMapper.selectOne(any())).thenReturn(existing);

        AttachmentView view = service.add(TASK_ID, new AddAttachmentRequest("1001", "审批依据.pdf"));

        assertEquals(9L, view.id());
        verify(attachmentMapper, never()).insert(any(TaskAttachment.class));
    }

    @Test
    @DisplayName("非办理人上传 → 30423（由 TaskGuard 拒绝）")
    void shouldRejectNonOperator() {
        when(taskGuard.requireWritableTask(TASK_ID))
                .thenThrow(new BizException(ErrorCode.FLOW_NO_PERMISSION, "无权处理该流程任务"));

        BizException e = assertThrows(BizException.class,
                () -> service.add(TASK_ID, new AddAttachmentRequest("1001", "a.pdf")));

        assertEquals(ErrorCode.FLOW_NO_PERMISSION.getCode(), e.getCode());
        verify(attachmentMapper, never()).insert(any(TaskAttachment.class));
    }

    @Test
    @DisplayName("任务已完成（运行库查不到）→ 30404，禁止补传")
    void shouldRejectAfterTaskCompleted() {
        when(taskGuard.requireWritableTask(TASK_ID))
                .thenThrow(new BizException(ErrorCode.FLOW_NOT_FOUND, "任务不存在或已处理"));

        BizException e = assertThrows(BizException.class,
                () -> service.add(TASK_ID, new AddAttachmentRequest("1001", "a.pdf")));

        assertEquals(ErrorCode.FLOW_NOT_FOUND.getCode(), e.getCode());
        verify(attachmentMapper, never()).insert(any(TaskAttachment.class));
    }

    @Test
    @DisplayName("fileId/fileName 为空或超长 → PARAM_INVALID")
    void shouldValidateRequest() {
        writableTask();

        assertEquals(ErrorCode.PARAM_INVALID.getCode(),
                assertThrows(BizException.class,
                        () -> service.add(TASK_ID, new AddAttachmentRequest(" ", "a.pdf"))).getCode());
        assertEquals(ErrorCode.PARAM_INVALID.getCode(),
                assertThrows(BizException.class,
                        () -> service.add(TASK_ID, new AddAttachmentRequest("1001", null))).getCode());
        assertEquals(ErrorCode.PARAM_INVALID.getCode(),
                assertThrows(BizException.class,
                        () -> service.add(TASK_ID, new AddAttachmentRequest("1".repeat(129), "a.pdf"))).getCode());
    }

    @Test
    @DisplayName("列表按任务查询并映射视图")
    void shouldListByTask() {
        TaskAttachment row = new TaskAttachment();
        row.setId(1L);
        row.setTaskId(TASK_ID);
        row.setFileId("1001");
        row.setFileName("a.pdf");
        when(attachmentMapper.selectList(any())).thenReturn(List.of(row));

        List<AttachmentView> views = service.list(TASK_ID);

        assertEquals(1, views.size());
        assertEquals("a.pdf", views.get(0).fileName());
    }
}

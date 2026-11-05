package com.hris.flow.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * 任务访问守卫单测（FLW-F-020）：办理人放行、非办理人拒绝、任务已完成/跨租户不可见。
 */
@ExtendWith(MockitoExtension.class)
class TaskGuardTest {

    private static final String TASK_ID = "t1";

    @Mock
    TaskService taskService;
    @Mock
    DelegateService delegateService;
    @Mock
    TaskQuery query;

    private TaskGuard guard;

    @BeforeEach
    void setUp() {
        guard = new TaskGuard(taskService, delegateService);
        TraceContext.setTenantId(100001L);
        TraceContext.setUserId(5L);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    private void stubTask(String assignee) {
        Task task = mock(Task.class);
        lenient().when(task.getId()).thenReturn(TASK_ID);
        lenient().when(task.getAssignee()).thenReturn(assignee);
        lenient().when(task.getProcessDefinitionId()).thenReturn("committeeFlow:1:9001");
        lenient().when(taskService.createTaskQuery()).thenReturn(query);
        lenient().when(query.taskId(TASK_ID)).thenReturn(query);
        lenient().when(query.taskTenantId("100001")).thenReturn(query);
        lenient().when(query.singleResult()).thenReturn(task);
        lenient().when(query.taskCandidateUser("5")).thenReturn(query);
        lenient().when(query.count()).thenReturn(0L);
    }

    @Test
    @DisplayName("当前 assignee 即办理人 → 放行")
    void shouldAllowAssignee() {
        stubTask("5");

        assertNotNull(guard.requireWritableTask(TASK_ID));
    }

    @Test
    @DisplayName("非 assignee、非候选人、无委托 → 30423")
    void shouldRejectNonOperator() {
        stubTask("9");

        BizException e = assertThrows(BizException.class, () -> guard.requireWritableTask(TASK_ID));

        assertEquals(ErrorCode.FLOW_NO_PERMISSION.getCode(), e.getCode());
    }

    @Test
    @DisplayName("任务不存在（已完成后从运行库消失）→ 30404，即完成后禁传")
    void shouldRejectMissingTask() {
        lenient().when(taskService.createTaskQuery()).thenReturn(query);
        lenient().when(query.taskId(TASK_ID)).thenReturn(query);
        lenient().when(query.taskTenantId("100001")).thenReturn(query);
        lenient().when(query.singleResult()).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> guard.requireWritableTask(TASK_ID));

        assertEquals(ErrorCode.FLOW_NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    @DisplayName("缺租户上下文 → 20501")
    void shouldRejectWithoutTenant() {
        TraceContext.clear();

        BizException e = assertThrows(BizException.class, () -> guard.requireTask(TASK_ID));

        assertEquals(ErrorCode.TENANT_INVALID.getCode(), e.getCode());
    }
}

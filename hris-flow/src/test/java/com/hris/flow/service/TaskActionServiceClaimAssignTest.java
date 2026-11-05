package com.hris.flow.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.TaskActionRequest;
import com.hris.flow.entity.TaskAction;
import com.hris.flow.enums.TaskActionType;
import com.hris.flow.mapper.TaskActionMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 待指派任务认领/指派单元测试（docs/19 §9-1）：
 * claim 幂等、已被认领拒绝、assign 管理动作强制指派（无需是 assignee）。
 */
@ExtendWith(MockitoExtension.class)
class TaskActionServiceClaimAssignTest {

    private static final String TASK_ID = "t1";

    @Mock
    TaskService taskService;
    @Mock
    RuntimeService runtimeService;
    @Mock
    TaskActionMapper taskActionMapper;
    @Mock
    BizRelationService bizRelationService;
    @Mock
    EventOutboxService eventOutboxService;
    @Mock
    InstanceService instanceService;
    @Mock
    ApprovalCallbackDispatcher callbackDispatcher;
    @Mock
    TaskQuery query;

    private TaskActionService service;

    @Mock
    HistoryService historyService;

    @BeforeEach
    void setUp() {
        service = new TaskActionService(taskService, runtimeService, historyService, taskActionMapper,
                bizRelationService, eventOutboxService, instanceService, callbackDispatcher);
        TraceContext.setTenantId(100001L);
        TraceContext.setUserId(5L);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    private void stubTask(String assignee) {
        Task task = org.mockito.Mockito.mock(Task.class);
        org.mockito.Mockito.lenient().when(task.getAssignee()).thenReturn(assignee);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId(TASK_ID)).thenReturn(query);
        when(query.taskTenantId("100001")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
    }

    @Test
    @DisplayName("认领无主任务（assignee 空串）→ changed=true，assignee 置为操作者并留痕 CLAIM")
    void claimShouldSucceedOnUnassigned() {
        stubTask("");
        TaskActionRequest req = new TaskActionRequest("我来处理", null, null, null);

        boolean changed = service.claim(TASK_ID, req);

        assertTrue(changed);
        verify(taskService).setAssignee(TASK_ID, "5");
        ArgumentCaptor<TaskAction> captor = ArgumentCaptor.forClass(TaskAction.class);
        verify(taskActionMapper).insert(captor.capture());
        assertEquals(TaskActionType.CLAIM.name(), captor.getValue().getAction());
        assertEquals(5L, captor.getValue().getOperator());
    }

    @Test
    @DisplayName("重复认领自己（幂等）→ changed=false，不再设置 assignee、不重复留痕")
    void claimShouldBeIdempotentForSelf() {
        stubTask("5");

        boolean changed = service.claim(TASK_ID, null);

        assertFalse(changed);
        verify(taskService, never()).setAssignee(any(), any());
        verify(taskActionMapper, never()).insert(any(TaskAction.class));
    }

    @Test
    @DisplayName("任务已被他人认领 → 30423 拒绝")
    void claimShouldRejectWhenClaimedByOther() {
        stubTask("9");

        BizException e = assertThrows(BizException.class, () -> service.claim(TASK_ID, null));

        assertEquals(ErrorCode.FLOW_NO_PERMISSION.getCode(), e.getCode());
        verify(taskService, never()).setAssignee(any(), any());
    }

    @Test
    @DisplayName("assign 管理动作：操作者无需是 assignee，强制指派给目标用户并留痕 ASSIGN")
    void assignShouldForceAssignToTarget() {
        stubTask("9");
        TaskActionRequest req = new TaskActionRequest("主管缺席，强制改派", null, 7L, null);

        service.assign(TASK_ID, req);

        verify(taskService).setAssignee(TASK_ID, "7");
        ArgumentCaptor<TaskAction> captor = ArgumentCaptor.forClass(TaskAction.class);
        verify(taskActionMapper).insert(captor.capture());
        assertEquals(TaskActionType.ASSIGN.name(), captor.getValue().getAction());
        assertEquals(9L, captor.getValue().getDelegateFrom());
    }

    @Test
    @DisplayName("assign 缺 targetUserId → PARAM_INVALID")
    void assignShouldRequireTarget() {
        stubTask("");

        BizException e = assertThrows(BizException.class, () -> service.assign(TASK_ID, null));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        verify(taskService, never()).setAssignee(any(), any());
    }
}

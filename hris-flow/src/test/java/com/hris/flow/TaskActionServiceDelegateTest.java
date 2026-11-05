package com.hris.flow.service;

import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.TaskActionRequest;
import com.hris.flow.entity.TaskAction;
import com.hris.flow.enums.TaskActionType;
import com.hris.flow.mapper.TaskActionMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 委托代办审批留痕单元测试（FLW-F-009）：被委托人（to_user）审批 from_user 名下任务时，
 * 留痕 operator=实际操作人（to_user）+ delegateFrom=委托发起人（from_user）。
 */
@ExtendWith(MockitoExtension.class)
class TaskActionServiceDelegateTest {

    private static final String TASK_ID = "t9";

    @Mock
    TaskService taskService;
    @Mock
    RuntimeService runtimeService;
    @Mock
    HistoryService historyService;
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
    DelegateService delegateService;
    @Mock
    TaskQuery query;
    @Mock
    TaskQuery candidateQuery;
    @Mock
    ProcessInstanceQuery piQuery;

    private TaskActionService service;

    @BeforeEach
    void setUp() {
        service = new TaskActionService(taskService, runtimeService, historyService, taskActionMapper,
                bizRelationService, eventOutboxService, instanceService, callbackDispatcher,
                delegateService);
        TraceContext.setTenantId(100001L);
        TraceContext.setUserId(7L);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    private Task stubTask(String assignee, String procDefId, String procInstId) {
        Task task = mock(Task.class);
        lenient().when(task.getAssignee()).thenReturn(assignee);
        lenient().when(task.getProcessDefinitionId()).thenReturn(procDefId);
        lenient().when(task.getProcessInstanceId()).thenReturn(procInstId);
        when(taskService.createTaskQuery()).thenReturn(query, candidateQuery);
        when(query.taskId(TASK_ID)).thenReturn(query);
        when(query.taskTenantId("100001")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
        return task;
    }

    private void stubCandidateZero() {
        when(candidateQuery.taskId(TASK_ID)).thenReturn(candidateQuery);
        when(candidateQuery.taskCandidateUser("7")).thenReturn(candidateQuery);
        when(candidateQuery.count()).thenReturn(0L);
    }

    private void stubInstanceAlive(String procInstId) {
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.processInstanceId(procInstId)).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(mock(ProcessInstance.class));
    }

    @Test
    @DisplayName("approve 委托代办：非 assignee 的被委托人可处理，留痕 operator=7 + delegateFrom=9")
    void approveShouldRecordDelegateFrom() {
        stubTask("9", "ATT_LEAVE:1:1", "pi-9");
        stubCandidateZero();
        when(delegateService.resolveDelegateFrom(7L, 100001L, "9", "ATT_LEAVE")).thenReturn(9L);
        stubInstanceAlive("pi-9");

        service.approve(TASK_ID, new TaskActionRequest("同意", null, null, null));

        verify(taskService).complete(TASK_ID);
        ArgumentCaptor<TaskAction> captor = ArgumentCaptor.forClass(TaskAction.class);
        verify(taskActionMapper).insert(captor.capture());
        assertEquals(TaskActionType.APPROVE.name(), captor.getValue().getAction());
        assertEquals(7L, captor.getValue().getOperator());
        assertEquals(9L, captor.getValue().getDelegateFrom());
    }

    @Test
    @DisplayName("approve 非委托：assignee 即操作人时 delegateFrom 为空，不做委托判定")
    void approveShouldNotRecordDelegateFromWithoutDelegation() {
        stubTask("7", "ATT_LEAVE:1:1", "pi-7");
        stubInstanceAlive("pi-7");
        // 显式打桩返回 null（mock 未打桩的 Long 默认返回 0 而非 null）
        when(delegateService.resolveDelegateFrom(any(), any(), any(), any())).thenReturn(null);

        service.approve(TASK_ID, new TaskActionRequest("同意", null, null, null));

        ArgumentCaptor<TaskAction> captor = ArgumentCaptor.forClass(TaskAction.class);
        verify(taskActionMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getOperator());
        assertNull(captor.getValue().getDelegateFrom());
    }
}

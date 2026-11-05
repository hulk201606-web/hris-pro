package com.hris.flow.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.TaskActionRequest;
import com.hris.flow.entity.BizRelation;
import com.hris.flow.entity.TaskAction;
import com.hris.flow.enums.InstanceStatus;
import com.hris.flow.enums.TaskActionType;
import com.hris.flow.mapper.TaskActionMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 驳回退回语义单元测试（FLW-F-007 一期深化）：
 * terminate=true 维持终止（删实例）；缺省=退回发起人（moveActivityIdTo 回起始任务 + 指派发起人 + 不删实例）。
 */
@ExtendWith(MockitoExtension.class)
class TaskActionServiceRejectTest {

    private static final String TASK_ID = "t2";
    private static final String PROC_INST_ID = "pi-1";

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

    private TaskActionService service;

    @BeforeEach
    void setUp() {
        service = new TaskActionService(taskService, runtimeService, historyService, taskActionMapper,
                bizRelationService, eventOutboxService, instanceService, callbackDispatcher,
                delegateService, org.mockito.Mockito.mock(TaskStrategyService.class),
                org.mockito.Mockito.mock(TaskOpinionService.class));
        TraceContext.setTenantId(100001L);
        TraceContext.setUserId(2L);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    private Task stubTask(String assignee) {
        Task task = mock(Task.class);
        lenient().when(task.getAssignee()).thenReturn(assignee);
        lenient().when(task.getProcessInstanceId()).thenReturn(PROC_INST_ID);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId(TASK_ID)).thenReturn(query);
        when(query.taskTenantId("100001")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
        return task;
    }

    private ProcessInstance stubRunningInstance(String activityId) {
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getActivityId()).thenReturn(activityId);
        when(pi.getStartUserId()).thenReturn("7");
        ProcessInstanceQuery piq = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piq);
        when(piq.processInstanceId(PROC_INST_ID)).thenReturn(piq);
        when(piq.singleResult()).thenReturn(pi);
        return pi;
    }

    private void stubFirstUserTask(String activityId) {
        HistoricActivityInstance hai = mock(HistoricActivityInstance.class);
        when(hai.getActivityId()).thenReturn(activityId);
        HistoricActivityInstanceQuery hq = mock(HistoricActivityInstanceQuery.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(hq);
        when(hq.processInstanceId(PROC_INST_ID)).thenReturn(hq);
        when(hq.activityType("userTask")).thenReturn(hq);
        when(hq.orderByHistoricActivityInstanceStartTime()).thenReturn(hq);
        when(hq.asc()).thenReturn(hq);
        when(hq.listPage(0, 1)).thenReturn(List.of(hai));
    }

    @Test
    @DisplayName("terminate=true：维持终止语义，删除实例并按 REJECTED 终态回调")
    void rejectShouldTerminateWhenFlagSet() {
        stubTask("2");
        BizRelation rel = new BizRelation();
        rel.setBizType("ATT_LEAVE");
        rel.setBizKey("ATT_LEAVE:1");
        rel.setTenantId(100001L);
        rel.setStatus(InstanceStatus.RUNNING.name());
        when(bizRelationService.requireByProcInstId(PROC_INST_ID)).thenReturn(rel);

        service.reject(TASK_ID, new TaskActionRequest("不批", null, null, true));

        verify(runtimeService).deleteProcessInstance(eq(PROC_INST_ID), anyString());
        verify(bizRelationService).updateStatus(rel, InstanceStatus.TERMINATED);
        verify(callbackDispatcher).dispatchOnTerminal(eq("ATT_LEAVE"), eq("ATT_LEAVE:1"),
                eq(100001L), eq(false), eq("REJECTED"), eq("不批"), eq(PROC_INST_ID));
        ArgumentCaptor<TaskAction> captor = ArgumentCaptor.forClass(TaskAction.class);
        verify(taskActionMapper).insert(captor.capture());
        assertEquals(TaskActionType.REJECT.name(), captor.getValue().getAction());
    }

    @Test
    @DisplayName("缺省 reject：退回发起人——moveActivityIdTo 回起始任务、指派发起人、不删实例")
    void rejectShouldReturnToInitiatorByDefault() {
        stubTask("2");
        stubRunningInstance("step2");
        stubFirstUserTask("step1");

        org.flowable.engine.runtime.ChangeActivityStateBuilder builder =
                mock(org.flowable.engine.runtime.ChangeActivityStateBuilder.class);
        when(runtimeService.createChangeActivityStateBuilder()).thenReturn(builder);
        when(builder.processInstanceId(PROC_INST_ID)).thenReturn(builder);
        when(builder.moveActivityIdTo("step2", "step1")).thenReturn(builder);

        TaskQuery targetQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query, targetQuery);
        Task target = mock(Task.class);
        when(target.getId()).thenReturn("t-new");
        when(targetQuery.processInstanceId(PROC_INST_ID)).thenReturn(targetQuery);
        when(targetQuery.taskDefinitionKey("step1")).thenReturn(targetQuery);
        when(targetQuery.list()).thenReturn(List.of(target));

        service.reject(TASK_ID, new TaskActionRequest("材料不符", null, null, null));

        verify(builder).changeState();
        verify(taskService).setAssignee("t-new", "7");
        verify(runtimeService, never()).deleteProcessInstance(any(), any());
        ArgumentCaptor<TaskAction> captor = ArgumentCaptor.forClass(TaskAction.class);
        verify(taskActionMapper).insert(captor.capture());
        assertEquals(TaskActionType.REJECT.name(), captor.getValue().getAction());
        assertEquals("驳回退回发起人重提：材料不符", captor.getValue().getComment());
    }

    @Test
    @DisplayName("无法确定发起人 → FLOW_STATE_CONFLICT，不移动活动")
    void rejectShouldFailWhenInitiatorUnknown() {
        stubTask("2");
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getStartUserId()).thenReturn("");
        ProcessInstanceQuery piq = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piq);
        when(piq.processInstanceId(PROC_INST_ID)).thenReturn(piq);
        when(piq.singleResult()).thenReturn(pi);
        when(runtimeService.getVariable(PROC_INST_ID, "initiator")).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> service.reject(TASK_ID, new TaskActionRequest(null, null, null, null)));

        assertEquals(ErrorCode.FLOW_STATE_CONFLICT.getCode(), e.getCode());
        verify(runtimeService, never()).createChangeActivityStateBuilder();
    }
}

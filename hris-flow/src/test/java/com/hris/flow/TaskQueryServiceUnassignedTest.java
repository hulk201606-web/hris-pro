package com.hris.flow;

import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.PageResult;
import com.hris.flow.dto.TaskView;
import com.hris.flow.mapper.BizRelationMapper;
import com.hris.flow.mapper.FlowModelMapper;
import com.hris.flow.mapper.TimeoutCfgMapper;
import com.hris.flow.service.TaskQueryService;
import org.flowable.engine.HistoryService;
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

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 待指派任务列表查询测试（docs/19 §9-1）：查询须同时覆盖 assignee 为 NULL 与空串两种无主任务，
 * 支持 procDefKey 可选过滤。
 */
@ExtendWith(MockitoExtension.class)
class TaskQueryServiceUnassignedTest {

    @Mock
    TaskService taskService;
    @Mock
    HistoryService historyService;
    @Mock
    BizRelationMapper bizRelationMapper;
    @Mock
    FlowModelMapper flowModelMapper;
    @Mock
    TimeoutCfgMapper timeoutCfgMapper;
    @Mock
    com.hris.flow.service.CcService ccService;
    @Mock
    TaskQuery query;

    private TaskQueryService service;

    @BeforeEach
    void setUp() {
        service = new TaskQueryService(taskService, historyService, bizRelationMapper,
                flowModelMapper, timeoutCfgMapper, ccService);
        TraceContext.setTenantId(100001L);
        TraceContext.setUserId(5L);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    private Task unassignedTask(String id, String assignee) {
        Task t = mock(Task.class);
        when(t.getId()).thenReturn(id);
        when(t.getProcessInstanceId()).thenReturn("pi-" + id);
        when(t.getProcessDefinitionId()).thenReturn("ATT_LEAVE:1:100");
        when(t.getTaskDefinitionKey()).thenReturn("supervisor");
        when(t.getName()).thenReturn("上级审批");
        when(t.getCreateTime()).thenReturn(new Date());
        when(t.getAssignee()).thenReturn(assignee);
        return t;
    }

    private void stubQuery() {
        List<Task> tasks = List.of(unassignedTask("t1", ""));
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskTenantId("100001")).thenReturn(query);
        when(query.or()).thenReturn(query);
        when(query.taskUnassigned()).thenReturn(query);
        when(query.taskAssignee("")).thenReturn(query);
        when(query.endOr()).thenReturn(query);
        when(query.count()).thenReturn(1L);
        when(query.orderByTaskCreateTime()).thenReturn(query);
        when(query.desc()).thenReturn(query);
        when(query.listPage(anyInt(), anyInt())).thenReturn(tasks);
        when(bizRelationMapper.selectList(any())).thenReturn(List.of());
        when(flowModelMapper.selectList(any())).thenReturn(List.of());
        when(timeoutCfgMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("unassigned 查询使用 or(taskUnassigned, taskAssignee='') 覆盖空串与 NULL 两种无主任务")
    void unassignedShouldFilterEmptyOrNullAssignee() {
        stubQuery();

        PageResult<TaskView> result = service.unassigned(1, 20, null);

        verify(query).taskUnassigned();
        verify(query).taskAssignee("");
        verify(query, never()).taskCandidateOrAssigned(any());
        assertEquals(1, result.records().size());
        assertEquals("", result.records().get(0).assignee());
        assertEquals("TODO", result.records().get(0).status());
    }

    @Test
    @DisplayName("procDefKey 传入时追加流程定义过滤；未传时不过滤")
    void unassignedShouldApplyProcDefKeyFilterOptionally() {
        stubQuery();
        when(query.processDefinitionKey("ATT_LEAVE")).thenReturn(query);

        service.unassigned(1, 20, "ATT_LEAVE");

        verify(query).processDefinitionKey("ATT_LEAVE");
    }
}

package com.hris.flow;

import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.PageResult;
import com.hris.flow.dto.TaskView;
import com.hris.flow.entity.FlowExtDelegate;
import com.hris.flow.mapper.BizRelationMapper;
import com.hris.flow.mapper.FlowModelMapper;
import com.hris.flow.mapper.TimeoutCfgMapper;
import com.hris.flow.service.CcService;
import com.hris.flow.service.DelegateService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 待办委托并入单元测试（FLW-F-009）：生效期内委托把 from_user 的待办
 * 并入 to_user（当前登录人）的待办结果，TaskView 以 delegatedFrom 标记来源。
 */
@ExtendWith(MockitoExtension.class)
class TaskQueryServiceDelegateTest {

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
    CcService ccService;
    @Mock
    DelegateService delegateService;
    @Mock
    TaskQuery ownQuery;
    @Mock
    TaskQuery delegateQuery;

    private TaskQueryService service;

    @BeforeEach
    void setUp() {
        service = new TaskQueryService(taskService, historyService, bizRelationMapper,
                flowModelMapper, timeoutCfgMapper, ccService, delegateService);
        TraceContext.setTenantId(100001L);
        TraceContext.setUserId(5L);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    private FlowExtDelegate delegation(Long fromUser, String procDefKey) {
        FlowExtDelegate d = new FlowExtDelegate();
        d.setId(1L);
        d.setFromUser(fromUser);
        d.setToUser(5L);
        d.setProcDefKey(procDefKey);
        d.setStartAt(LocalDateTime.now().minusDays(1));
        d.setEndAt(LocalDateTime.now().plusDays(1));
        d.setStatus(DelegateService.STATUS_ACTIVE);
        return d;
    }

    private Task task(String procInstId) {
        Task t = mock(Task.class);
        lenient().when(t.getProcessInstanceId()).thenReturn(procInstId);
        lenient().when(t.getProcessDefinitionId()).thenReturn("ATT_LEAVE:1:1");
        lenient().when(t.getName()).thenReturn("部门审批");
        lenient().when(t.getCreateTime()).thenReturn(new java.util.Date());
        return t;
    }

    @Test
    @DisplayName("todo 并入：生效委托生效中，from_user=9 的待办并入当前登录人(5)结果并标记 delegatedFrom=9")
    void todoShouldMergeDelegatedTasks() {
        when(taskService.createTaskQuery()).thenReturn(ownQuery, delegateQuery);
        when(ownQuery.taskTenantId("100001")).thenReturn(ownQuery);
        when(ownQuery.taskCandidateOrAssigned("5")).thenReturn(ownQuery);
        when(ownQuery.count()).thenReturn(0L);
        when(ownQuery.orderByTaskCreateTime()).thenReturn(ownQuery);
        when(ownQuery.desc()).thenReturn(ownQuery);
        when(ownQuery.listPage(anyInt(), anyInt())).thenReturn(List.of());
        when(delegateQuery.taskTenantId("100001")).thenReturn(delegateQuery);
        when(delegateQuery.taskCandidateOrAssigned("9")).thenReturn(delegateQuery);
        when(delegateQuery.count()).thenReturn(1L);
        when(delegateQuery.orderByTaskCreateTime()).thenReturn(delegateQuery);
        when(delegateQuery.desc()).thenReturn(delegateQuery);
        lenient().when(bizRelationMapper.selectList(any())).thenReturn(List.of());
        lenient().when(timeoutCfgMapper.selectList(any())).thenReturn(List.of());
        lenient().when(flowModelMapper.selectList(any())).thenReturn(List.of());
        // 注意：先构造 Task mock 再进入 when(...)，避免在未完成的打桩内再打桩
        List<Task> delegateTasks = List.of(task("pi-9"));
        when(delegateService.activeDelegationsTo(any(), any(), any()))
                .thenReturn(List.of(delegation(9L, null)));
        when(delegateQuery.listPage(anyInt(), anyInt())).thenReturn(delegateTasks);

        PageResult<TaskView> result = service.todo(1, 20);

        assertEquals(1L, result.total());
        assertEquals(1, result.records().size());
        assertEquals("9", String.valueOf(result.records().get(0).delegatedFrom()));
        assertEquals("部门审批", result.records().get(0).nodeName());
    }

    @Test
    @DisplayName("todo 并入：无生效委托时结果与自身待办一致，不发起第二次任务查询")
    void todoShouldNotMergeWithoutActiveDelegation() {
        when(taskService.createTaskQuery()).thenReturn(ownQuery);
        when(ownQuery.taskTenantId("100001")).thenReturn(ownQuery);
        when(ownQuery.taskCandidateOrAssigned("5")).thenReturn(ownQuery);
        when(ownQuery.count()).thenReturn(2L);
        when(ownQuery.orderByTaskCreateTime()).thenReturn(ownQuery);
        when(ownQuery.desc()).thenReturn(ownQuery);
        when(ownQuery.listPage(anyInt(), anyInt())).thenReturn(List.of());
        when(delegateService.activeDelegationsTo(any(), any(), any())).thenReturn(List.of());

        PageResult<TaskView> result = service.todo(1, 20);

        assertEquals(2L, result.total());
        assertEquals(0, result.records().size());
    }
}

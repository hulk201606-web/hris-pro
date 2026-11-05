package com.hris.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.entity.TaskAction;
import com.hris.flow.entity.TimeoutCfg;
import com.hris.flow.enums.TaskActionType;
import com.hris.flow.mapper.TaskActionMapper;
import com.hris.flow.mapper.TimeoutCfgMapper;
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

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务维护操作单元测试（docs/19 §10.5-C/§10.5-A2）：
 * 超时升级（超期判定/幂等/升级赋值）+ 负责人变更待办重算（查询条件/赋值/幂等）。
 */
@ExtendWith(MockitoExtension.class)
class TaskMaintenanceServiceTest {

    @Mock
    TaskService taskService;
    @Mock
    TaskActionMapper taskActionMapper;
    @Mock
    TimeoutCfgMapper timeoutCfgMapper;
    @Mock
    TaskQuery query;

    private TaskMaintenanceService service;

    @BeforeEach
    void setUp() {
        service = new TaskMaintenanceService(taskService, taskActionMapper, timeoutCfgMapper);
        TraceContext.setTenantId(100001L);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    private void stubRunningTasks(Task... tasks) {
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskTenantId("100001")).thenReturn(query);
        when(query.active()).thenReturn(query);
        when(query.list()).thenReturn(List.of(tasks));
    }

    private Task task(String id, String defId, String nodeKey, String assignee, Date createTime) {
        Task t = mock(Task.class);
        org.mockito.Mockito.lenient().when(t.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(t.getProcessDefinitionId()).thenReturn(defId);
        org.mockito.Mockito.lenient().when(t.getTaskDefinitionKey()).thenReturn(nodeKey);
        org.mockito.Mockito.lenient().when(t.getAssignee()).thenReturn(assignee);
        org.mockito.Mockito.lenient().when(t.getCreateTime()).thenReturn(createTime);
        return t;
    }

    private TimeoutCfg cfg(String defId, String nodeKey, Integer hours, Long escalateTo) {
        TimeoutCfg c = new TimeoutCfg();
        c.setProcDefId(defId);
        c.setNodeId(nodeKey);
        c.setEscalateAfterH(hours);
        c.setEscalateTo(escalateTo);
        return c;
    }

    @Test
    @DisplayName("超时升级：已超期且配置 escalate_to → setAssignee 升级目标 + 留痕 ESCALATE")
    void escalateShouldReassignAndRecord() {
        Task t = task("t1", "ATT_LEAVE:1:100", "supervisor", "5",
                new Date(System.currentTimeMillis() - 3_600_000L * 5));
        stubRunningTasks(t);
        when(timeoutCfgMapper.selectList(any()))
                .thenReturn(List.of(cfg("ATT_LEAVE:1:100", "supervisor", 2, 9L)));

        Map<String, Integer> result = service.escalateOverdue();

        assertEquals(1, result.get("escalated"));
        verify(taskService).setAssignee("t1", "9");
        ArgumentCaptor<TaskAction> captor = ArgumentCaptor.forClass(TaskAction.class);
        verify(taskActionMapper).insert(captor.capture());
        assertEquals(TaskActionType.ESCALATE.name(), captor.getValue().getAction());
        assertEquals(5L, captor.getValue().getDelegateFrom());
        assertEquals(0L, captor.getValue().getOperator());
    }

    @Test
    @DisplayName("幂等：已升级过（task_action 存在 ESCALATE）→ 跳过不重复赋值")
    void escalateShouldBeIdempotent() {
        Task t = task("t1", "ATT_LEAVE:1:100", "supervisor", "9",
                new Date(System.currentTimeMillis() - 3_600_000L * 5));
        stubRunningTasks(t);
        when(timeoutCfgMapper.selectList(any()))
                .thenReturn(List.of(cfg("ATT_LEAVE:1:100", "supervisor", 2, 9L)));
        when(taskActionMapper.selectCount(any())).thenReturn(1L);

        Map<String, Integer> result = service.escalateOverdue();

        assertEquals(1, result.get("skippedIdempotent"));
        assertEquals(0, result.get("escalated"));
        verify(taskService, never()).setAssignee(any(), any());
        verify(taskActionMapper, never()).insert(any(TaskAction.class));
    }

    @Test
    @DisplayName("未超期/未配置：不升级；超期但无 escalate_to：仅留痕不改派")
    void escalateShouldRespectCfgAndOverdue() {
        Task fresh = task("t1", "ATT_LEAVE:1:100", "supervisor", "5", new Date());
        Task noTarget = task("t2", "ATT_OT:1:200", "mgr", "6",
                new Date(System.currentTimeMillis() - 3_600_000L * 10));
        stubRunningTasks(fresh, noTarget);
        when(timeoutCfgMapper.selectList(any())).thenReturn(List.of(
                cfg("ATT_LEAVE:1:100", "supervisor", 2, 9L),
                cfg("ATT_OT:1:200", "mgr", 4, null)));

        Map<String, Integer> result = service.escalateOverdue();

        assertEquals(1, result.get("escalated"));
        verify(taskService, never()).setAssignee("t1", "9");
        verify(taskService, never()).setAssignee(org.mockito.ArgumentMatchers.eq("t2"), any());
        verify(taskActionMapper).insert(any(TaskAction.class));
    }

    @Test
    @DisplayName("负责人变更重算：assignee=oldEmpId 的运行中任务改派 newEmpId 并留痕 ASSIGN")
    void reassignManagerShouldMoveAssigneeAndRecord() {
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskTenantId("100001")).thenReturn(query);
        when(query.taskAssignee("8")).thenReturn(query);
        when(query.active()).thenReturn(query);
        Task t1 = task("t1", null, null, "8", null);
        Task t2 = task("t2", null, null, "8", null);
        when(query.list()).thenReturn(List.of(t1, t2));

        Map<String, Integer> result = service.reassignManager(8L, 12L);

        assertEquals(2, result.get("reassigned"));
        verify(taskService).setAssignee("t1", "12");
        verify(taskService).setAssignee("t2", "12");
        ArgumentCaptor<TaskAction> captor = ArgumentCaptor.forClass(TaskAction.class);
        verify(taskActionMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals(TaskActionType.ASSIGN.name(), captor.getAllValues().get(0).getAction());
        assertEquals(8L, captor.getAllValues().get(0).getDelegateFrom());
        assertTrue(captor.getAllValues().get(0).getComment().contains("负责人变更重算"));
    }

    @Test
    @DisplayName("负责人变更重算幂等：无 oldEmpId 任务时 reassigned=0")
    void reassignManagerShouldBeIdempotent() {
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskTenantId("100001")).thenReturn(query);
        when(query.taskAssignee("8")).thenReturn(query);
        when(query.active()).thenReturn(query);
        when(query.list()).thenReturn(List.of());

        Map<String, Integer> result = service.reassignManager(8L, 12L);

        assertEquals(0, result.get("reassigned"));
        verify(taskService, never()).setAssignee(any(), any());
        verify(taskActionMapper, never()).insert(any(TaskAction.class));
    }

    @Test
    @DisplayName("reassignManager 缺参 → PARAM_INVALID")
    void reassignManagerShouldValidateParams() {
        BizException e = assertThrows(BizException.class, () -> service.reassignManager(8L, null));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
    }
}

package com.hris.flow;

import com.hris.flow.dto.PageResult;
import com.hris.flow.dto.TaskView;
import com.hris.flow.entity.BizRelation;
import com.hris.flow.entity.FlowModel;
import com.hris.flow.entity.TimeoutCfg;
import com.hris.flow.mapper.BizRelationMapper;
import com.hris.flow.mapper.FlowModelMapper;
import com.hris.flow.mapper.TimeoutCfgMapper;
import com.hris.flow.service.TaskQueryService;
import com.hris.common.trace.TraceContext;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 待办查询死代码激活测试（docs/19 §8.7-6）：form_schema_json 随待办返回 + 超期 overdue 标记。
 */
@ExtendWith(MockitoExtension.class)
class TaskQueryServiceActivationTest {

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
    com.hris.flow.service.DelegateService delegateService;

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

    private Task task(String id, String procDefId, String nodeKey, Date createTime) {
        Task t = mock(Task.class);
        lenient().when(t.getId()).thenReturn(id);
        lenient().when(t.getProcessInstanceId()).thenReturn("pi-" + id);
        lenient().when(t.getProcessDefinitionId()).thenReturn(procDefId);
        lenient().when(t.getTaskDefinitionKey()).thenReturn(nodeKey);
        lenient().when(t.getName()).thenReturn("上级审批");
        lenient().when(t.getCreateTime()).thenReturn(createTime);
        return t;
    }

    @Test
    @DisplayName("待办返回模型 form_schema_json；配置超时且已超期 → overdue=true")
    void todoShouldExposeFormSchemaAndOverdue() {
        Task t = task("t1", "ATT_LEAVE:1:100", "supervisor", new Date(System.currentTimeMillis() - 3_600_000L * 5));

        TaskQuery q = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(q);
        when(q.taskTenantId("100001")).thenReturn(q);
        when(q.taskCandidateOrAssigned("5")).thenReturn(q);
        when(q.count()).thenReturn(1L);
        when(q.orderByTaskCreateTime()).thenReturn(q);
        when(q.desc()).thenReturn(q);
        when(q.listPage(anyInt(), anyInt())).thenReturn(List.of(t));

        BizRelation rel = new BizRelation();
        rel.setProcInstId("pi-t1");
        rel.setBizType("ATT_LEAVE");
        rel.setBizKey("ATT_LEAVE:1");
        when(bizRelationMapper.selectList(any())).thenReturn(List.of(rel));

        FlowModel model = new FlowModel();
        model.setProcDefKey("ATT_LEAVE");
        model.setVersion(1);
        model.setFormSchemaJson("{\"fields\":[{\"key\":\"days\"}]}");
        when(flowModelMapper.selectList(any())).thenReturn(List.of(model));

        TimeoutCfg cfg = new TimeoutCfg();
        cfg.setProcDefId("ATT_LEAVE:1:100");
        cfg.setNodeId("supervisor");
        cfg.setEscalateAfterH(2);
        when(timeoutCfgMapper.selectList(any())).thenReturn(List.of(cfg));

        PageResult<TaskView> result = service.todo(1, 20);

        assertEquals(1, result.records().size());
        TaskView view = result.records().get(0);
        assertEquals("{\"fields\":[{\"key\":\"days\"}]}", view.formSchemaJson());
        assertTrue(view.overdue(), "创建已 5 小时、阈值 2 小时 → 应标记超期");
    }

    @Test
    @DisplayName("未配置超时或未超期 → overdue=false；模型无表单 → formSchemaJson=null")
    void todoShouldBeFalseWhenNotOverdue() {
        Task t = task("t2", "ATT_OT:1:200", "supervisor", new Date());

        TaskQuery q = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(q);
        when(q.taskTenantId("100001")).thenReturn(q);
        when(q.taskCandidateOrAssigned("5")).thenReturn(q);
        when(q.count()).thenReturn(1L);
        when(q.orderByTaskCreateTime()).thenReturn(q);
        when(q.desc()).thenReturn(q);
        when(q.listPage(anyInt(), anyInt())).thenReturn(List.of(t));

        when(bizRelationMapper.selectList(any())).thenReturn(List.of());
        when(flowModelMapper.selectList(any())).thenReturn(List.of());
        when(timeoutCfgMapper.selectList(any())).thenReturn(List.of());

        PageResult<TaskView> result = service.todo(1, 20);

        TaskView view = result.records().get(0);
        assertEquals("t2", view.taskId());
        assertEquals(null, view.formSchemaJson());
        assertFalse(view.overdue());
    }
}

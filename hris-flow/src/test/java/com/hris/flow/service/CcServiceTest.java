package com.hris.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.PageResult;
import com.hris.flow.dto.TaskView;
import com.hris.flow.entity.BizRelation;
import com.hris.flow.entity.FlowCc;
import com.hris.flow.mapper.BizRelationMapper;
import com.hris.flow.mapper.FlowCcMapper;
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
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 抄送真实化单元测试（FLW-F-011）：启动写入/补抄送去重/cc 查询按当前用户过滤。
 */
@ExtendWith(MockitoExtension.class)
class CcServiceTest {

    @Mock
    FlowCcMapper flowCcMapper;
    @Mock
    BizRelationMapper bizRelationMapper;
    @Mock
    TaskService taskService;
    @Mock
    TaskQuery query;

    private CcService service;

    @BeforeEach
    void setUp() {
        service = new CcService(flowCcMapper, bizRelationMapper, taskService);
        TraceContext.setTenantId(100001L);
        TraceContext.setUserId(5L);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("recordOnStart：按 empId 去重写入，空列表/非法值跳过")
    void recordOnStartShouldWriteDistinctEmpIds() {
        int n = service.recordOnStart("pi-1", "step1", java.util.Arrays.asList(5L, 5L, 6L, null, 0L));

        assertEquals(2, n);
        ArgumentCaptor<FlowCc> captor = ArgumentCaptor.forClass(FlowCc.class);
        verify(flowCcMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals(5L, captor.getAllValues().get(0).getEmpId());
        assertEquals(6L, captor.getAllValues().get(1).getEmpId());
        assertEquals("step1", captor.getAllValues().get(0).getCcNode());
    }

    @Test
    @DisplayName("addCc：实例不存在 → FLOW_NOT_FOUND")
    void addCcShouldRejectUnknownInstance() {
        when(bizRelationMapper.selectOne(any())).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> service.addCc("pi-x", List.of(5L)));

        assertEquals(ErrorCode.FLOW_NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    @DisplayName("addCc：已抄送（uk 命中）幂等跳过，仅新增未抄送人")
    void addCcShouldBeIdempotentPerNode() {
        BizRelation rel = new BizRelation();
        rel.setProcInstId("pi-1");
        rel.setBizType("ATT_LEAVE");
        when(bizRelationMapper.selectOne(any())).thenReturn(rel);
        Task t = mock(Task.class);
        when(t.getTaskDefinitionKey()).thenReturn("step2");
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.processInstanceId("pi-1")).thenReturn(query);
        when(query.list()).thenReturn(List.of(t));
        // emp 5 已抄送，emp 7 未抄送；并发兜底 DuplicateKeyException 也忽略
        when(flowCcMapper.selectCount(any())).thenReturn(1L).thenReturn(0L);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("dup"))
                .when(flowCcMapper).insert(any(FlowCc.class));

        int n = service.addCc("pi-1", List.of(5L, 7L));

        assertEquals(0, n);
    }

    @Test
    @DisplayName("pageMine：按当前用户 emp_id 过滤，字段对齐 TaskView")
    void pageMineShouldFilterByCurrentUser() {
        FlowCc cc = new FlowCc();
        cc.setProcInstId("pi-1");
        cc.setCcNode("step1");
        cc.setEmpId(5L);
        when(flowCcMapper.selectCount(any())).thenReturn(1L);
        when(flowCcMapper.selectList(any())).thenReturn(List.of(cc));
        BizRelation rel = new BizRelation();
        rel.setProcInstId("pi-1");
        rel.setBizType("ATT_LEAVE");
        rel.setBizKey("ATT_LEAVE:9");
        rel.setStatus("RUNNING");
        when(bizRelationMapper.selectList(any())).thenReturn(List.of(rel));

        PageResult<TaskView> page = service.pageMine(1, 20);

        assertEquals(1L, page.total());
        TaskView view = page.records().get(0);
        assertEquals("pi-1", view.instanceId());
        assertEquals("ATT_LEAVE", view.bizType());
        assertEquals("ATT_LEAVE:9", view.bizKey());
        assertEquals("step1", view.nodeName());
        assertEquals("RUNNING", view.status());

        // 过滤条件：wrapper 含 emp_id = 当前用户 5
        ArgumentCaptor<LambdaQueryWrapper<FlowCc>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(flowCcMapper).selectCount(captor.capture());
        String segment = captor.getValue().getSqlSegment().toUpperCase();
        assertTrue(segment.contains("EMP_ID"), "查询应包含 emp_id 条件");
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(5L),
                "emp_id 条件值应为当前用户 5");
    }
}

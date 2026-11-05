package com.hris.flow;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.TaskActionRequest;
import com.hris.flow.mapper.TaskActionMapper;
import com.hris.flow.service.ApprovalCallbackDispatcher;
import com.hris.flow.service.BizRelationService;
import com.hris.flow.service.EventOutboxService;
import com.hris.flow.service.InstanceService;
import com.hris.flow.service.TaskActionService;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 驳回退回-发起人重提闭环（FLW-F-007 一期深化）：Flowable 内存引擎真实语义验证。
 *
 * <p>场景：两级审批（step1 → step2），上级在 step2 驳回 → 实例仍 RUNNING、活动回到 step1、
 * assignee=发起人；发起人在退回任务上 approve → 续跑至 step2 再 approve → 实例终结。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskActionServiceRejectResubmitEngineTest {

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://hris.com/test">
                <process id="rejflow" name="驳回退回测试流程" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="step1"/>
                    <userTask id="step1" name="发起节点" flowable:assignee="${starter}"/>
                    <sequenceFlow id="f2" sourceRef="step1" targetRef="step2"/>
                    <userTask id="step2" name="上级审批" flowable:assignee="2"/>
                    <sequenceFlow id="f3" sourceRef="step2" targetRef="end"/>
                    <endEvent id="end"/>
                </process>
            </definitions>
            """;

    private ProcessEngine engine;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private TaskActionService actionService;

    @BeforeAll
    void setUpEngine() {
        engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
                .setJdbcUrl("jdbc:h2:mem:flowreject;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
                .setJdbcDriver("org.h2.Driver")
                .setJdbcUsername("sa")
                .setJdbcPassword("")
                .setAsyncExecutorActivate(false)
                .buildProcessEngine();
        runtimeService = engine.getRuntimeService();
        taskService = engine.getTaskService();
        engine.getRepositoryService().createDeployment()
                .addString("rejflow.bpmn20.xml", BPMN)
                .tenantId("100001")
                .deploy();

        // 依赖协程全部 mock：留痕落库/出站/回调不参与本测试断言
        TaskActionMapper taskActionMapper = mock(TaskActionMapper.class);
        BizRelationService bizRelationService = mock(BizRelationService.class);
        when(bizRelationService.requireByProcInstId(anyString()))
                .thenThrow(new BizException(ErrorCode.FLOW_NOT_FOUND, "无映射"));
        actionService = new TaskActionService(taskService, runtimeService,
                engine.getHistoryService(), taskActionMapper, bizRelationService,
                mock(EventOutboxService.class), mock(InstanceService.class),
                mock(ApprovalCallbackDispatcher.class));
    }

    @AfterAll
    void tearDownEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    @BeforeEach
    void setUpContext() {
        TraceContext.setTenantId(100001L);
    }

    @AfterEach
    void clearContext() {
        TraceContext.clear();
    }

    private ProcessInstance start() {
        Authentication.setAuthenticatedUserId("7");
        try {
            return runtimeService.startProcessInstanceByKeyAndTenantId("rejflow", "biz-1",
                    Map.of("starter", "1"), "100001");
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }
    }

    private Task activeTask(String procInstId) {
        return taskService.createTaskQuery().processInstanceId(procInstId).singleResult();
    }

    @Test
    @DisplayName("reject → 实例仍 RUNNING、活动回到起始任务、assignee=发起人；再 approve 终结实例")
    void rejectShouldReturnToInitiatorAndResubmitCompletes() {
        TraceContext.setUserId(1L);
        ProcessInstance pi = start();
        String procInstId = pi.getId();

        // 发起节点处理，推进到 step2
        actionService.approve(activeTask(procInstId).getId(), null);
        Task step2 = activeTask(procInstId);
        assertEquals("step2", step2.getTaskDefinitionKey());

        // 上级驳回（缺省 terminate=false → 退回发起人）
        TraceContext.setUserId(2L);
        actionService.reject(step2.getId(), new TaskActionRequest("材料不符", null, null, null));

        // 实例仍 RUNNING，活动回到起始任务 step1，assignee=发起人 7
        assertNotNull(runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).singleResult(), "退回后实例应保持运行中");
        Task returned = activeTask(procInstId);
        assertEquals("step1", returned.getTaskDefinitionKey(), "活动应回到起始用户任务");
        assertEquals("7", returned.getAssignee(), "退回任务应指派给发起人");

        // 发起人重提：approve 续跑至 step2
        TraceContext.setUserId(7L);
        actionService.approve(returned.getId(), null);
        Task step2Again = activeTask(procInstId);
        assertEquals("step2", step2Again.getTaskDefinitionKey());

        // 终审通过 → 实例终结
        TraceContext.setUserId(2L);
        actionService.approve(step2Again.getId(), null);
        assertNull(runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).singleResult(), "重提终审通过后实例应终结");
    }

    @Test
    @DisplayName("terminate=true → 实例被删除（终止语义与退回语义分离）")
    void rejectWithTerminateShouldDeleteInstance() {
        TraceContext.setUserId(1L);
        ProcessInstance pi = start();
        String procInstId = pi.getId();
        actionService.approve(activeTask(procInstId).getId(), null);

        TraceContext.setUserId(2L);
        actionService.reject(activeTask(procInstId).getId(),
                new TaskActionRequest("作废", null, null, true));

        assertNull(runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).singleResult(), "terminate=true 应删除实例");
    }
}

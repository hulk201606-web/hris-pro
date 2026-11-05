package com.hris.flow;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.TaskActionRequest;
import com.hris.flow.mapper.TaskActionMapper;
import com.hris.flow.service.ApprovalCallbackDispatcher;
import com.hris.flow.service.BizRelationService;
import com.hris.flow.service.DelegateService;
import com.hris.flow.service.EventOutboxService;
import com.hris.flow.service.InstanceService;
import com.hris.flow.service.TaskActionService;
import com.hris.flow.service.TaskOpinionService;
import com.hris.flow.service.TaskStrategyService;
import com.hris.flow.strategy.ApprovalStrategyType;
import com.hris.flow.strategy.NodeStrategySpec;
import com.hris.flow.strategy.StrategyDecision;
import com.hris.flow.strategy.StrategyOutcome;
import com.hris.flow.strategy.StrategyVote;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会签策略接线验证（FLW-F-008）：策略结论驱动任务动作的三种走向，以及审批动作内联意见标签。
 *
 * <p>引擎用内存 H2 真实语义（任务完成/活动回退/实例终止），仅策略评估与意见标签落库为 mock，
 * 以便验证「接线点」而非引擎行为。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskActionServiceStrategyWiringTest {

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://hris.com/test">
                <process id="voteflow" name="会签接线测试流程" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="step1"/>
                    <userTask id="step1" name="发起节点" flowable:assignee="${starter}"/>
                    <sequenceFlow id="f2" sourceRef="step1" targetRef="step2"/>
                    <userTask id="step2" name="会签节点" flowable:assignee="2"/>
                    <sequenceFlow id="f3" sourceRef="step2" targetRef="end"/>
                    <endEvent id="end"/>
                </process>
            </definitions>
            """;

    private ProcessEngine engine;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private TaskStrategyService strategyService;
    private TaskOpinionService opinionService;
    private TaskActionService actionService;

    @BeforeAll
    void setUpEngine() {
        engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
                .setJdbcUrl("jdbc:h2:mem:flowvote;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
                .setJdbcDriver("org.h2.Driver")
                .setJdbcUsername("sa")
                .setJdbcPassword("")
                .setAsyncExecutorActivate(false)
                .buildProcessEngine();
        runtimeService = engine.getRuntimeService();
        taskService = engine.getTaskService();
        engine.getRepositoryService().createDeployment()
                .addString("voteflow.bpmn20.xml", BPMN)
                .tenantId("100001")
                .deploy();

        strategyService = mock(TaskStrategyService.class);
        opinionService = mock(TaskOpinionService.class);
        BizRelationService bizRelationService = mock(BizRelationService.class);
        when(bizRelationService.requireByProcInstId(anyString()))
                .thenThrow(new BizException(ErrorCode.FLOW_NOT_FOUND, "无映射"));
        actionService = new TaskActionService(taskService, runtimeService, engine.getHistoryService(),
                mock(TaskActionMapper.class), bizRelationService, mock(EventOutboxService.class),
                mock(InstanceService.class), mock(ApprovalCallbackDispatcher.class),
                mock(DelegateService.class), strategyService, opinionService);
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
        // 共享引擎与 mock：逐用例重置策略/标签桩，避免上个用例的结论泄漏到下一个
        org.mockito.Mockito.reset(strategyService, opinionService);
    }

    @AfterEach
    void clearContext() {
        TraceContext.clear();
    }

    private ProcessInstance startAndAdvance() {
        Authentication.setAuthenticatedUserId("7");
        ProcessInstance pi;
        try {
            pi = runtimeService.startProcessInstanceByKeyAndTenantId("voteflow", "biz-1",
                    Map.of("starter", "1"), "100001");
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }
        TraceContext.setUserId(1L);
        actionService.approve(activeTask(pi.getId()).getId(), null);
        return pi;
    }

    private Task activeTask(String procInstId) {
        return taskService.createTaskQuery().processInstanceId(procInstId).singleResult();
    }

    private static StrategyOutcome percentOutcome(StrategyDecision decision) {
        NodeStrategySpec spec = new NodeStrategySpec(ApprovalStrategyType.PERCENT, 60);
        return StrategyOutcome.of(decision, spec, new StrategyVote(60, 1, 3, 1, false));
    }

    @Test
    @DisplayName("比例未达标（策略判定 REJECT）→ 任务不完成，退回发起人重提")
    void approveShouldReturnToInitiatorWhenPercentNotReached() {
        ProcessInstance pi = startAndAdvance();
        when(strategyService.evaluate(any(Task.class), eq(true)))
                .thenReturn(Optional.of(percentOutcome(StrategyDecision.REJECT)));
        TraceContext.setUserId(2L);
        Task task = activeTask(pi.getId());

        actionService.approve(task.getId(), new TaskActionRequest("同意", null, null, null));

        assertNotNull(runtimeService.createProcessInstanceQuery()
                .processInstanceId(pi.getId()).singleResult(), "未达标不应结束实例");
        Task returned = activeTask(pi.getId());
        assertEquals("step1", returned.getTaskDefinitionKey(), "应退回起始节点");
        assertEquals("7", returned.getAssignee(), "退回任务指派发起人");
    }

    @Test
    @DisplayName("一票否决（策略判定 REJECT）→ 整体终止，实例被删除")
    void rejectShouldTerminateWhenVetoed() {
        ProcessInstance pi = startAndAdvance();
        when(strategyService.evaluate(any(Task.class), eq(false)))
                .thenReturn(Optional.of(percentOutcome(StrategyDecision.REJECT)));
        TraceContext.setUserId(2L);

        actionService.reject(activeTask(pi.getId()).getId(), new TaskActionRequest("不同意", null, null, null));

        assertNull(runtimeService.createProcessInstanceQuery()
                .processInstanceId(pi.getId()).singleResult(), "一票否决应终止实例");
    }

    @Test
    @DisplayName("策略未配置（空结果）→ 原行为：任务完成、标签随动作落库")
    void approveShouldKeepLegacyBehaviourWithoutStrategy() {
        ProcessInstance pi = startAndAdvance();
        when(strategyService.evaluate(any(Task.class), eq(true))).thenReturn(Optional.empty());
        TraceContext.setUserId(2L);
        Task task = activeTask(pi.getId());

        actionService.approve(task.getId(),
                new TaskActionRequest("同意", null, null, null, null, "COMPLIANT"));

        verify(opinionService).recordForAction(task.getId(), "COMPLIANT", "同意");
        assertNull(runtimeService.createProcessInstanceQuery()
                .processInstanceId(pi.getId()).singleResult(), "终审通过后实例应结束");
    }
}

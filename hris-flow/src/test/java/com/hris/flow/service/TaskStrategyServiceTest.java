package com.hris.flow.service;

import com.hris.flow.strategy.ApprovalStrategyFactory;
import com.hris.flow.strategy.ApprovalStrategyType;
import com.hris.flow.strategy.NodeStrategyResolver;
import com.hris.flow.strategy.NodeStrategySpec;
import com.hris.flow.strategy.PercentStrategy;
import com.hris.flow.strategy.StrategyDecision;
import com.hris.flow.strategy.StrategyOutcome;
import com.hris.flow.strategy.VetoStrategy;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会签策略驱动单测（FLW-F-008）：计票汇总、达标关停剩余任务、一票否决、未配置零副作用。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskStrategyServiceTest {

    private static final String PROC_INST = "pi-1";
    private static final String PROC_DEF = "committeeFlow:1:9001";
    private static final String NODE = "committee";
    private static final String TASK_ID = "t1";
    private static final String EXEC_ID = "e1";

    @Mock
    TaskService taskService;
    @Mock
    RuntimeService runtimeService;
    @Mock
    NodeStrategyResolver resolver;

    private TaskStrategyService service;
    private Task current;

    @BeforeEach
    void setUp() {
        ApprovalStrategyFactory factory = new ApprovalStrategyFactory(
                List.of(new PercentStrategy(), new VetoStrategy()));
        service = new TaskStrategyService(taskService, runtimeService, resolver, factory);
        current = mock(Task.class);
        lenient().when(current.getId()).thenReturn(TASK_ID);
        lenient().when(current.getProcessInstanceId()).thenReturn(PROC_INST);
        lenient().when(current.getProcessDefinitionId()).thenReturn(PROC_DEF);
        lenient().when(current.getTaskDefinitionKey()).thenReturn(NODE);
        lenient().when(current.getExecutionId()).thenReturn(EXEC_ID);
    }

    private void stubSpec(ApprovalStrategyType type, int threshold) {
        when(resolver.resolve(PROC_DEF, NODE)).thenReturn(Optional.of(new NodeStrategySpec(type, threshold)));
    }

    @Test
    @DisplayName("3 人会签 60%，首票同意 → WAIT，写回计票变量且不关停任何任务")
    void shouldWaitOnFirstVote() {
        stubSpec(ApprovalStrategyType.PERCENT, 60);
        when(runtimeService.getVariable(EXEC_ID, "nrOfInstances")).thenReturn(3);

        StrategyOutcome outcome = service.evaluate(current, true).orElseThrow();

        assertEquals(StrategyDecision.WAIT, outcome.decision());
        assertEquals(1, outcome.vote().approvedCount());
        assertEquals(3, outcome.vote().totalCount());
        verify(runtimeService).setVariables(eq(PROC_INST), anyMap());
        verify(taskService, never()).complete(anyString(), anyMap());
    }

    @Test
    @DisplayName("3 人会签 60%，第 2 票同意 → PASS，关停同节点剩余任务（不含当前任务）")
    void shouldPassAndCloseSiblingsWhenThresholdReached() {
        stubSpec(ApprovalStrategyType.PERCENT, 60);
        when(runtimeService.getVariable(EXEC_ID, "nrOfInstances")).thenReturn(3);
        when(runtimeService.getVariable(PROC_INST, "flow_approved_" + NODE)).thenReturn(1);
        Task sibling = mock(Task.class);
        lenient().when(sibling.getId()).thenReturn("t2");
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.processInstanceId(PROC_INST)).thenReturn(query);
        when(query.taskDefinitionKey(NODE)).thenReturn(query);
        when(query.list()).thenReturn(List.of(current, sibling));

        StrategyOutcome outcome = service.evaluate(current, true).orElseThrow();

        assertEquals(StrategyDecision.PASS, outcome.decision());
        assertEquals(2, outcome.vote().approvedCount());
        verify(taskService).complete(eq("t2"), anyMap());
        verify(taskService, never()).complete(eq(TASK_ID), anyMap());
    }

    @Test
    @DisplayName("一票否决节点出现否决票 → REJECT")
    void shouldRejectOnVeto() {
        stubSpec(ApprovalStrategyType.VETO, 60);
        when(runtimeService.getVariable(EXEC_ID, "nrOfInstances")).thenReturn(3);

        StrategyOutcome outcome = service.evaluate(current, false).orElseThrow();

        assertEquals(StrategyDecision.REJECT, outcome.decision());
        assertTrue(outcome.vote().vetoed());
    }

    @Test
    @DisplayName("未配置策略的节点 → 空结果，不读写任何流程变量")
    void shouldBeTransparentWhenNotConfigured() {
        when(resolver.resolve(PROC_DEF, NODE)).thenReturn(Optional.empty());

        assertTrue(service.evaluate(current, true).isEmpty());
        verify(runtimeService, never()).setVariables(anyString(), anyMap());
        verify(taskService, never()).complete(anyString(), anyMap());
    }
}

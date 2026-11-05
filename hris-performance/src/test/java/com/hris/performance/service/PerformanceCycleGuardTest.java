package com.hris.performance.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.performance.dto.EvalTaskDtos.EvalTaskCreateRequest;
import com.hris.performance.dto.EvaluationDtos.EvaluationCreateRequest;
import com.hris.performance.dto.GoalDtos.GoalCreateRequest;
import com.hris.performance.dto.GoalDtos.GoalUpdateRequest;
import com.hris.performance.entity.Cycle;
import com.hris.performance.entity.EvalTask;
import com.hris.performance.entity.Evaluation;
import com.hris.performance.entity.Goal;
import com.hris.performance.mapper.CycleMapper;
import com.hris.performance.mapper.EvalTaskMapper;
import com.hris.performance.mapper.EvaluationMapper;
import com.hris.performance.mapper.GoalMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 绩效子对象周期状态守卫单测（PRF-F-002）：目标/评价任务/评分在各周期状态下的写拒绝矩阵。
 *
 * <p>口径：目标与任务仅 DRAFT/ACTIVE 可写；SCORING 起冻结、CLOSED 全锁；
 * 评分按角色区分——自评只开 ACTIVE 窗口，上级/互评开 ACTIVE+SCORING。
 * Evaluation 无 cycleId，经 taskId → EvalTask → cycleId 两跳定位周期。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("绩效子对象周期状态守卫")
class PerformanceCycleGuardTest {

    private static final Long CYCLE_ID = 600001L;
    private static final Long GOAL_ID = 610001L;
    private static final Long TASK_ID = 620001L;
    private static final Long EVAL_ID = 630001L;

    @Mock
    private GoalMapper goalMapper;
    @Mock
    private EvalTaskMapper evalTaskMapper;
    @Mock
    private EvaluationMapper evaluationMapper;
    @Mock
    private CycleMapper cycleMapper;

    private Cycle cycle(String status) {
        Cycle c = new Cycle();
        c.setId(CYCLE_ID);
        c.setStatus(status);
        return c;
    }

    private Goal goal(Long cycleId) {
        Goal g = new Goal();
        g.setId(GOAL_ID);
        g.setCycleId(cycleId);
        g.setEmpId(200001L);
        g.setWeight(new BigDecimal("50"));
        return g;
    }

    private EvalTask task(Long cycleId, String role) {
        EvalTask t = new EvalTask();
        t.setId(TASK_ID);
        t.setCycleId(cycleId);
        t.setEmpId(200001L);
        t.setEvaluatorRole(role);
        return t;
    }

    private Evaluation evaluation(Long taskId) {
        Evaluation e = new Evaluation();
        e.setId(EVAL_ID);
        e.setTaskId(taskId);
        e.setEvaluatorId(200001L);
        return e;
    }

    private GoalCreateRequest goalCreate(String title, BigDecimal weight) {
        return new GoalCreateRequest(CYCLE_ID, 200001L, title, "KPI", weight, null, null);
    }

    @Nested
    @DisplayName("GoalService")
    class GoalGuard {

        private GoalService service;

        @BeforeEach
        void setUp() {
            service = new GoalService(goalMapper, cycleMapper);
        }

        @Test
        @DisplayName("create：SCORING（评分阶段）→ PRF_ERROR，不落库")
        void rejectsCreateWhenScoring() {
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("SCORING"));

            BizException e = assertThrows(BizException.class,
                    () -> service.create(goalCreate("目标", new BigDecimal("50"))));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(goalMapper, never()).insert(any(Goal.class));
        }

        @Test
        @DisplayName("create：CLOSED → PRF_ERROR（周期已关闭）")
        void rejectsCreateWhenClosed() {
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("CLOSED"));

            assertThrows(BizException.class,
                    () -> service.create(goalCreate("目标", new BigDecimal("50"))));
            verify(goalMapper, never()).insert(any(Goal.class));
        }

        @Test
        @DisplayName("update：原周期 CLOSED → 拒绝")
        void rejectsUpdateWhenClosed() {
            when(goalMapper.selectById(GOAL_ID)).thenReturn(goal(CYCLE_ID));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("CLOSED"));

            BizException e = assertThrows(BizException.class, () -> service.update(GOAL_ID,
                    new GoalUpdateRequest(CYCLE_ID, 200001L, "改目标", "KPI",
                            new BigDecimal("50"), null, null)));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(goalMapper, never()).updateById(any(Goal.class));
        }

        @Test
        @DisplayName("delete：SCORING → 拒绝（评分后不可删目标）")
        void rejectsDeleteWhenScoring() {
            when(goalMapper.selectById(GOAL_ID)).thenReturn(goal(CYCLE_ID));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("SCORING"));

            assertThrows(BizException.class, () -> service.delete(GOAL_ID));
            verify(goalMapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("create：ACTIVE（目标可调整）→ 正常落库")
        void allowsCreateWhenActive() {
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("ACTIVE"));
            when(goalMapper.insert(any(Goal.class))).thenReturn(1);

            service.create(goalCreate("目标", new BigDecimal("50")));

            verify(goalMapper).insert(any(Goal.class));
        }
    }

    @Nested
    @DisplayName("EvalTaskService")
    class EvalTaskGuard {

        private EvalTaskService service;

        @BeforeEach
        void setUp() {
            service = new EvalTaskService(evalTaskMapper, cycleMapper);
        }

        @Test
        @DisplayName("create：SCORING → PRF_ERROR，不落库")
        void rejectsCreateWhenScoring() {
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("SCORING"));

            BizException e = assertThrows(BizException.class,
                    () -> service.create(new EvalTaskCreateRequest(CYCLE_ID, 200001L,
                            "SUPERVISOR", "PENDING")));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(evalTaskMapper, never()).insert(any(EvalTask.class));
        }

        @Test
        @DisplayName("delete：CLOSED → 拒绝")
        void rejectsDeleteWhenClosed() {
            when(evalTaskMapper.selectById(TASK_ID)).thenReturn(task(CYCLE_ID, "SELF"));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("CLOSED"));

            assertThrows(BizException.class, () -> service.delete(TASK_ID));
            verify(evalTaskMapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("create：ACTIVE（启动期生成任务树）→ 正常落库")
        void allowsCreateWhenActive() {
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("ACTIVE"));
            when(evalTaskMapper.insert(any(EvalTask.class))).thenReturn(1);

            service.create(new EvalTaskCreateRequest(CYCLE_ID, 200001L,
                    "SUPERVISOR", "PENDING"));

            verify(evalTaskMapper).insert(any(EvalTask.class));
        }
    }

    @Nested
    @DisplayName("EvaluationService（taskId → EvalTask → cycleId）")
    class EvaluationGuard {

        private EvaluationService service;

        @BeforeEach
        void setUp() {
            service = new EvaluationService(evaluationMapper, evalTaskMapper, cycleMapper);
        }

        @Test
        @DisplayName("自评：DRAFT → 拒绝（周期未启动）")
        void rejectsSelfWhenDraft() {
            when(evalTaskMapper.selectById(TASK_ID)).thenReturn(task(CYCLE_ID, "SELF"));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("DRAFT"));

            BizException e = assertThrows(BizException.class,
                    () -> service.create(new EvaluationCreateRequest(TASK_ID, 200001L, "{}", "自评")));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(evaluationMapper, never()).insert(any(Evaluation.class));
        }

        @Test
        @DisplayName("自评：SCORING → 拒绝（自评窗口已关闭）")
        void rejectsSelfWhenScoring() {
            when(evalTaskMapper.selectById(TASK_ID)).thenReturn(task(CYCLE_ID, "SELF"));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("SCORING"));

            assertThrows(BizException.class,
                    () -> service.create(new EvaluationCreateRequest(TASK_ID, 200001L, "{}", "改自评")));
            verify(evaluationMapper, never()).insert(any(Evaluation.class));
        }

        @Test
        @DisplayName("自评：ACTIVE → 放行")
        void allowsSelfWhenActive() {
            when(evalTaskMapper.selectById(TASK_ID)).thenReturn(task(CYCLE_ID, "SELF"));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("ACTIVE"));
            when(evaluationMapper.insert(any(Evaluation.class))).thenReturn(1);

            service.create(new EvaluationCreateRequest(TASK_ID, 200001L, "{}", "自评"));

            verify(evaluationMapper).insert(any(Evaluation.class));
        }

        @Test
        @DisplayName("上级评分：SCORING → 放行")
        void allowsSupervisorWhenScoring() {
            when(evalTaskMapper.selectById(TASK_ID)).thenReturn(task(CYCLE_ID, "SUPERVISOR"));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("SCORING"));
            when(evaluationMapper.insert(any(Evaluation.class))).thenReturn(1);

            service.create(new EvaluationCreateRequest(TASK_ID, 200002L, "{}", "上级评"));

            verify(evaluationMapper).insert(any(Evaluation.class));
        }

        @Test
        @DisplayName("上级评分：CLOSED → 拒绝")
        void rejectsSupervisorWhenClosed() {
            when(evalTaskMapper.selectById(TASK_ID)).thenReturn(task(CYCLE_ID, "SUPERVISOR"));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("CLOSED"));

            assertThrows(BizException.class,
                    () -> service.create(new EvaluationCreateRequest(TASK_ID, 200002L, "{}", "评")));
            verify(evaluationMapper, never()).insert(any(Evaluation.class));
        }

        @Test
        @DisplayName("delete：所属周期 CLOSED → 拒绝")
        void rejectsDeleteWhenClosed() {
            when(evaluationMapper.selectById(EVAL_ID)).thenReturn(evaluation(TASK_ID));
            when(evalTaskMapper.selectById(TASK_ID)).thenReturn(task(CYCLE_ID, "SUPERVISOR"));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("CLOSED"));

            assertThrows(BizException.class, () -> service.delete(EVAL_ID));
            verify(evaluationMapper, never()).deleteById(any(Long.class));
        }
    }
}

package com.hris.performance.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.performance.dto.EvalTaskDtos.EvalTaskCreateRequest;
import com.hris.performance.dto.EvaluationDtos.EvaluationCreateRequest;
import com.hris.performance.dto.GoalDtos.GoalCreateRequest;
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
 * 绩效子对象周期锁守卫单测（§8.5-4）：所属周期 PUBLISHED/CLOSED 时，
 * Goal/EvalTask/Evaluation 的 create/update/delete 一律拒绝。
 *
 * <p>Evaluation 无 cycleId，经 taskId → EvalTask → cycleId 两跳定位周期。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("绩效子对象周期锁守卫")
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
        return g;
    }

    private EvalTask task(Long cycleId) {
        EvalTask t = new EvalTask();
        t.setId(TASK_ID);
        t.setCycleId(cycleId);
        return t;
    }

    private Evaluation evaluation(Long taskId) {
        Evaluation e = new Evaluation();
        e.setId(EVAL_ID);
        e.setTaskId(taskId);
        return e;
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
        @DisplayName("create：周期 PUBLISHED → PRF_ERROR，不落库")
        void rejectsCreateWhenPublished() {
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("PUBLISHED"));

            BizException e = assertThrows(BizException.class,
                    () -> service.create(new GoalCreateRequest(CYCLE_ID, 200001L, "目标",
                            new BigDecimal("50"), null, null)));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(goalMapper, never()).insert(any(Goal.class));
        }

        @Test
        @DisplayName("update：原周期 CLOSED → 拒绝")
        void rejectsUpdateWhenClosed() {
            when(goalMapper.selectById(GOAL_ID)).thenReturn(goal(CYCLE_ID));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("CLOSED"));

            BizException e = assertThrows(BizException.class, () -> service.update(GOAL_ID,
                    new com.hris.performance.dto.GoalDtos.GoalUpdateRequest(
                            CYCLE_ID, 200001L, "改目标", new BigDecimal("50"), null, null)));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(goalMapper, never()).updateById(any(Goal.class));
        }

        @Test
        @DisplayName("delete：周期 CLOSED → 拒绝")
        void rejectsDeleteWhenClosed() {
            when(goalMapper.selectById(GOAL_ID)).thenReturn(goal(CYCLE_ID));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("CLOSED"));

            BizException e = assertThrows(BizException.class, () -> service.delete(GOAL_ID));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(goalMapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("create：周期 ACTIVE → 正常落库")
        void allowsCreateWhenActive() {
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("ACTIVE"));
            when(goalMapper.insert(any(Goal.class))).thenReturn(1);

            service.create(new GoalCreateRequest(CYCLE_ID, 200001L, "目标",
                    new BigDecimal("50"), null, null));

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
        @DisplayName("create：周期 PUBLISHED → PRF_ERROR，不落库")
        void rejectsCreateWhenPublished() {
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("PUBLISHED"));

            BizException e = assertThrows(BizException.class,
                    () -> service.create(new EvalTaskCreateRequest(CYCLE_ID, 200001L,
                            "SUPERVISOR", "PENDING")));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(evalTaskMapper, never()).insert(any(EvalTask.class));
        }

        @Test
        @DisplayName("update：周期 CLOSED → 拒绝")
        void rejectsUpdateWhenClosed() {
            when(evalTaskMapper.selectById(TASK_ID)).thenReturn(task(CYCLE_ID));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("CLOSED"));

            BizException e = assertThrows(BizException.class, () -> service.update(TASK_ID,
                    new com.hris.performance.dto.EvalTaskDtos.EvalTaskUpdateRequest(
                            CYCLE_ID, 200001L, "SUPERVISOR", "DONE")));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(evalTaskMapper, never()).updateById(any(EvalTask.class));
        }

        @Test
        @DisplayName("delete：周期 CLOSED → 拒绝")
        void rejectsDeleteWhenClosed() {
            when(evalTaskMapper.selectById(TASK_ID)).thenReturn(task(CYCLE_ID));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("CLOSED"));

            BizException e = assertThrows(BizException.class, () -> service.delete(TASK_ID));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(evalTaskMapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("create：周期 ACTIVE → 正常落库")
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
        @DisplayName("create：所属周期 PUBLISHED → PRF_ERROR，不落库")
        void rejectsCreateWhenPublished() {
            when(evalTaskMapper.selectById(TASK_ID)).thenReturn(task(CYCLE_ID));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("PUBLISHED"));

            BizException e = assertThrows(BizException.class,
                    () -> service.create(new EvaluationCreateRequest(TASK_ID, 200002L,
                            "{}", "评语")));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(evaluationMapper, never()).insert(any(Evaluation.class));
        }

        @Test
        @DisplayName("update：所属周期 PUBLISHED → 拒绝")
        void rejectsUpdateWhenPublished() {
            when(evaluationMapper.selectById(EVAL_ID)).thenReturn(evaluation(TASK_ID));
            when(evalTaskMapper.selectById(TASK_ID)).thenReturn(task(CYCLE_ID));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("PUBLISHED"));

            BizException e = assertThrows(BizException.class, () -> service.update(EVAL_ID,
                    new com.hris.performance.dto.EvaluationDtos.EvaluationUpdateRequest(
                            TASK_ID, 200002L, "{}", "改评语")));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(evaluationMapper, never()).updateById(any(Evaluation.class));
        }

        @Test
        @DisplayName("delete：所属周期 CLOSED → 拒绝")
        void rejectsDeleteWhenClosed() {
            when(evaluationMapper.selectById(EVAL_ID)).thenReturn(evaluation(TASK_ID));
            when(evalTaskMapper.selectById(TASK_ID)).thenReturn(task(CYCLE_ID));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("CLOSED"));

            BizException e = assertThrows(BizException.class, () -> service.delete(EVAL_ID));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(evaluationMapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("create：所属周期 ACTIVE → 正常落库")
        void allowsCreateWhenActive() {
            when(evalTaskMapper.selectById(TASK_ID)).thenReturn(task(CYCLE_ID));
            when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle("ACTIVE"));
            when(evaluationMapper.insert(any(Evaluation.class))).thenReturn(1);

            service.create(new EvaluationCreateRequest(TASK_ID, 200002L, "{}", "评语"));

            verify(evaluationMapper).insert(any(Evaluation.class));
        }
    }
}

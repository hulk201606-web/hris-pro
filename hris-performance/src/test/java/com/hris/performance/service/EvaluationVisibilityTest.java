package com.hris.performance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.common.security.AuthContextHolder;
import com.hris.performance.dto.EvaluationDtos.EvaluationResponse;
import com.hris.performance.entity.Cycle;
import com.hris.performance.entity.EvalTask;
import com.hris.performance.entity.Evaluation;
import com.hris.performance.mapper.CycleMapper;
import com.hris.performance.mapper.EvalTaskMapper;
import com.hris.performance.mapper.EvaluationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 评价详情/列表互见掩码单测（PRF-F-004）：越权视角 scores/comment 置 null，字段本身保留。
 *
 * <p>调用链：AuthContextHolder（网关透传的当前用户/权限）→ ScoreVisibilityRule 判定 →
 * 响应组装。userId 与 empId 同源（MVP 口径，见 InterviewController#currentEmpId）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("评价详情互见掩码")
class EvaluationVisibilityTest {

    private static final Long CYCLE_ID = 600001L;
    private static final Long EVAL_ID = 630001L;
    private static final Long TASK_ID = 620001L;
    private static final Long EMP_ID = 200001L;
    private static final Long SUPERVISOR_ID = 200002L;

    @Mock
    private EvaluationMapper evaluationMapper;
    @Mock
    private EvalTaskMapper evalTaskMapper;
    @Mock
    private CycleMapper cycleMapper;

    private EvaluationService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationService(evaluationMapper, evalTaskMapper, cycleMapper);
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    private EvalTask task(String role) {
        EvalTask t = new EvalTask();
        t.setId(TASK_ID);
        t.setCycleId(CYCLE_ID);
        t.setEmpId(EMP_ID);
        t.setEvaluatorRole(role);
        return t;
    }

    private Cycle cycle(String status) {
        Cycle c = new Cycle();
        c.setId(CYCLE_ID);
        c.setStatus(status);
        return c;
    }

    private Evaluation evaluation(Long authorId) {
        Evaluation e = new Evaluation();
        e.setId(EVAL_ID);
        e.setTaskId(TASK_ID);
        e.setEvaluatorId(authorId);
        e.setScores("{\"total\":88}");
        e.setComment("过程评价");
        return e;
    }

    private EvaluationResponse detail(Evaluation e, EvalTask t, String cycleStatus) {
        when(evaluationMapper.selectById(EVAL_ID)).thenReturn(e);
        when(evalTaskMapper.selectById(TASK_ID)).thenReturn(t);
        when(cycleMapper.selectById(CYCLE_ID)).thenReturn(cycle(cycleStatus));
        return service.get(EVAL_ID);
    }

    @Test
    @DisplayName("员工看上级评分：SCORING 阶段置 null（字段保留），CLOSED 放开")
    void employeeViewsSupervisorScore() {
        AuthContextHolder.setUserId(EMP_ID);
        Evaluation e = evaluation(SUPERVISOR_ID);

        EvaluationResponse masked = detail(e, task("SUPERVISOR"), "SCORING");
        assertNull(masked.scores());
        assertNull(masked.comment());
        assertNotNull(masked.taskId(), "响应字段保留，仅置空值");
        assertNotNull(masked.evaluatorId());

        EvaluationResponse visible = detail(e, task("SUPERVISOR"), "CLOSED");
        assertEquals("{\"total\":88}", visible.scores());
    }

    @Test
    @DisplayName("上级看员工自评分：PREPARING（ACTIVE）不可见，SCORING（窗口关闭）可见")
    void supervisorViewsSelfScore() {
        AuthContextHolder.setUserId(SUPERVISOR_ID);
        Evaluation e = evaluation(EMP_ID);

        assertNull(detail(e, task("SELF"), "ACTIVE").scores());
        assertEquals("{\"total\":88}", detail(e, task("SELF"), "SCORING").scores());
    }

    @Test
    @DisplayName("HR 看自评分：同样需等自评窗口关闭")
    void hrViewsSelfScore() {
        AuthContextHolder.setUserId(900001L);
        AuthContextHolder.setPerms(Set.of("prf:cycle:write"));
        Evaluation e = evaluation(EMP_ID);

        assertNull(detail(e, task("SELF"), "ACTIVE").scores());
        assertEquals("{\"total\":88}", detail(e, task("SELF"), "CLOSED").scores());
    }

    @Test
    @DisplayName("作者看自己写的评价：任何阶段可见")
    void authorSeesOwn() {
        AuthContextHolder.setUserId(SUPERVISOR_ID);
        Evaluation e = evaluation(SUPERVISOR_ID);

        assertEquals("{\"total\":88}", detail(e, task("SUPERVISOR"), "ACTIVE").scores());
    }

    @Test
    @DisplayName("列表：逐条按视角掩码（员工 SCORING 只见自评、不见上级评）")
    void listMasksPerRow() {
        AuthContextHolder.setUserId(EMP_ID);
        Evaluation selfEval = evaluation(EMP_ID);
        selfEval.setId(1L);
        EvalTask supTask = task("SUPERVISOR");
        supTask.setId(2L);
        Evaluation supEval = evaluation(SUPERVISOR_ID);
        supEval.setId(2L);
        supEval.setTaskId(2L);

        Page<Evaluation> page = new Page<>(1, 20);
        page.setRecords(List.of(selfEval, supEval));
        page.setTotal(2);
        when(evaluationMapper.selectPage(any(), any())).thenReturn(page);
        when(evalTaskMapper.selectBatchIds(any())).thenReturn(List.of(task("SELF"), supTask));
        when(cycleMapper.selectBatchIds(any())).thenReturn(List.of(cycle("SCORING")));

        var result = service.list(1, 20);

        assertEquals(2, result.records().size());
        assertEquals("{\"total\":88}", result.records().get(0).scores());
        assertNull(result.records().get(1).scores());
    }

    @Test
    @DisplayName("任务缺失（脏数据）：fail-closed，全部置 null")
    void missingTaskFailsClosed() {
        AuthContextHolder.setUserId(EMP_ID);
        when(evaluationMapper.selectById(EVAL_ID)).thenReturn(evaluation(EMP_ID));
        when(evalTaskMapper.selectById(anyLong())).thenReturn(null);

        EvaluationResponse resp = service.get(EVAL_ID);

        assertNull(resp.scores());
        assertNull(resp.comment());
    }
}

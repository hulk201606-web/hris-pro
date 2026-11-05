package com.hris.performance.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.performance.dto.GoalDtos.GoalBatchItem;
import com.hris.performance.dto.GoalDtos.GoalBatchSaveRequest;
import com.hris.performance.dto.GoalDtos.GoalCreateRequest;
import com.hris.performance.entity.Cycle;
import com.hris.performance.entity.Goal;
import com.hris.performance.mapper.CycleMapper;
import com.hris.performance.mapper.GoalMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权重合计校验服务层单测（PRF-F-003）。
 *
 * <p>批量整体提交（bizKey = cycleId:empId:dimension）：先聚合校验合计 = 100 再落库，
 * 校验失败不得触达 delete/insert（避免"删了旧的、新的校验失败"）；单条录入只拦超 100。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("目标权重合计校验")
class GoalWeightServiceTest {

    private static final Long CYCLE_ID = 600001L;
    private static final Long EMP_ID = 200001L;

    @Mock
    private GoalMapper goalMapper;
    @Mock
    private CycleMapper cycleMapper;

    private GoalService service;

    @BeforeEach
    void setUp() {
        service = new GoalService(goalMapper, cycleMapper);
    }

    private void cycleStatus(String status) {
        Cycle c = new Cycle();
        c.setId(CYCLE_ID);
        c.setStatus(status);
        when(cycleMapper.selectById(CYCLE_ID)).thenReturn(c);
    }

    private Goal goal(Long id, String weight) {
        Goal g = new Goal();
        g.setId(id);
        g.setCycleId(CYCLE_ID);
        g.setEmpId(EMP_ID);
        g.setDimension("KPI");
        g.setWeight(new BigDecimal(weight));
        return g;
    }

    private GoalCreateRequest createReq(String weight) {
        return new GoalCreateRequest(CYCLE_ID, EMP_ID, "目标", "KPI", new BigDecimal(weight), null, null);
    }

    @Nested
    @DisplayName("批量整体提交")
    class Batch {

        @Test
        @DisplayName("合计 = 100：先校验后整体替换（delete + 2 insert）")
        void savesWhenTotalIsHundred() {
            cycleStatus("DRAFT");
            when(goalMapper.insert(any(Goal.class))).thenReturn(1);

            var resp = service.batchSave(new GoalBatchSaveRequest(CYCLE_ID, EMP_ID, "KPI", List.of(
                    new GoalBatchItem("营收", new BigDecimal("60"), "1亿", null),
                    new GoalBatchItem("回款", new BigDecimal("40"), "8000万", null))));

            assertEquals(2, resp.size());
            verify(goalMapper).delete(any());
            verify(goalMapper, times(2)).insert(any(Goal.class));
        }

        @Test
        @DisplayName("合计 = 85：PARAM_INVALID 附实际合计，且不 delete / 不 insert")
        void rejectsWhenTotalNotHundred() {
            cycleStatus("ACTIVE");

            BizException e = assertThrows(BizException.class,
                    () -> service.batchSave(new GoalBatchSaveRequest(CYCLE_ID, EMP_ID, "KPI", List.of(
                            new GoalBatchItem("营收", new BigDecimal("50"), null, null),
                            new GoalBatchItem("回款", new BigDecimal("35"), null, null)))));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            assertTrue(e.getMessage().contains("85"), "应附实际合计: " + e.getMessage());
            verify(goalMapper, never()).delete(any());
            verify(goalMapper, never()).insert(any(Goal.class));
        }

        @Test
        @DisplayName("合计 = 110：PARAM_INVALID（超差）")
        void rejectsWhenTotalExceeds() {
            cycleStatus("DRAFT");

            BizException e = assertThrows(BizException.class,
                    () -> service.batchSave(new GoalBatchSaveRequest(CYCLE_ID, EMP_ID, "KPI", List.of(
                            new GoalBatchItem("营收", new BigDecimal("60"), null, null),
                            new GoalBatchItem("回款", new BigDecimal("50"), null, null)))));

            assertTrue(e.getMessage().contains("110"));
        }

        @Test
        @DisplayName("SCORING 阶段：状态守卫优先，PRF_ERROR")
        void rejectsWhenScoring() {
            cycleStatus("SCORING");

            BizException e = assertThrows(BizException.class,
                    () -> service.batchSave(new GoalBatchSaveRequest(CYCLE_ID, EMP_ID, "KPI", List.of(
                            new GoalBatchItem("营收", new BigDecimal("100"), null, null)))));

            assertEquals(ErrorCode.PRF_ERROR.getCode(), e.getCode());
            verify(goalMapper, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("单条录入")
    class Single {

        @Test
        @DisplayName("已有 60 + 新增 50 = 110：PARAM_INVALID，不落库")
        void rejectsOverAllocation() {
            cycleStatus("DRAFT");
            when(goalMapper.selectList(any())).thenReturn(List.of(goal(1L, "60")));

            BizException e = assertThrows(BizException.class, () -> service.create(createReq("50")));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            assertTrue(e.getMessage().contains("110"));
            verify(goalMapper, never()).insert(any(Goal.class));
        }

        @Test
        @DisplayName("已有 40 + 新增 60 = 100：放行")
        void allowsExactHundred() {
            cycleStatus("ACTIVE");
            when(goalMapper.selectList(any())).thenReturn(List.of(goal(1L, "40")));
            when(goalMapper.insert(any(Goal.class))).thenReturn(1);

            service.create(createReq("60"));

            verify(goalMapper).insert(any(Goal.class));
        }

        @Test
        @DisplayName("合计不足 100：单条录入阶段放行（整体提交时才要求 = 100）")
        void allowsPartialWhileEntering() {
            cycleStatus("DRAFT");
            when(goalMapper.selectList(any())).thenReturn(List.of(goal(1L, "40")));
            when(goalMapper.insert(any(Goal.class))).thenReturn(1);

            service.create(createReq("30"));

            verify(goalMapper).insert(any(Goal.class));
        }
    }
}

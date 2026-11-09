package com.hris.compensation.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 算薪引擎单测（CMP-F-005/006 + 批 6 F-010 + 批 8 #18a）：
 * 状态与期间锁守卫、失败隔离、重算规则、缺省 NET 公式、
 * 考勤变量 live 注入与 fail-open 降级、异步流转与幂等。
 */
@ExtendWith(MockitoExtension.class)
class PayrollCalcServiceTest {

    static {
        // upsertTask 用 LambdaUpdateWrapper 操作 CalcTask，单测无 MP 环境需手动初始化
        // lambda cache（否则 "can not find lambda cache for this entity"）。
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), com.hris.compensation.entity.CalcTask.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), com.hris.compensation.entity.PayrollRun.class);
    }

    private static final Long RUN_ID = 800001L;

    @Mock
    private com.hris.compensation.mapper.PayrollRunMapper runMapper;
    @Mock
    private com.hris.compensation.mapper.PayrollDetailMapper detailMapper;
    @Mock
    private com.hris.compensation.mapper.EmployeePayMapper employeePayMapper;
    @Mock
    private com.hris.compensation.mapper.PayItemMapper payItemMapper;
    @Mock
    private com.hris.compensation.mapper.CalcTaskMapper calcTaskMapper;
    @Mock
    private PeriodLockService periodLockService;
    @Mock
    private AttendanceBriefFetcher attendanceFetcher;
    @Mock
    private PayrollCalcAsyncRunner asyncRunner;
    @Mock
    private CmpCalcConfigService calcConfigService;

    private PayrollCalcService service;
    private final List<com.hris.compensation.entity.CalcTask> insertedTasks = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(calcConfigService.payTerminationDay()).thenReturn(true);
        service = new PayrollCalcService(runMapper, detailMapper, employeePayMapper,
                payItemMapper, calcTaskMapper, periodLockService, new FormulaSandboxService(),
                new PayItemEvaluator(new FormulaSandboxService()),
                new CalcResultWriter(detailMapper, calcTaskMapper),
                attendanceFetcher, asyncRunner, new SalaryProrationService(), calcConfigService);
    }

    private com.hris.compensation.entity.PayrollRun run(String status) {
        var e = new com.hris.compensation.entity.PayrollRun();
        e.setId(RUN_ID);
        e.setPeriodCode("2026-01");
        e.setPayGroupId(1L);
        e.setStatus(status);
        return e;
    }

    private com.hris.compensation.entity.PayItem item(String code, String category, int seq, String formula) {
        var e = new com.hris.compensation.entity.PayItem();
        e.setId(seq * 10L);
        e.setPayGroupId(1L);
        e.setCode(code);
        e.setCategory(category);
        e.setSeq(seq);
        e.setFormula(formula);
        e.setStatus(1);
        return e;
    }

    private com.hris.compensation.entity.EmployeePay empPay(Long empId, String amount) {
        var e = new com.hris.compensation.entity.EmployeePay();
        e.setEmpId(empId);
        e.setPayGroupId(1L);
        e.setSalaryAmountCipher(amount);
        return e;
    }


    private void stubTaskAbsent() {
        lenient().when(calcTaskMapper.selectOne(any())).thenReturn(null);
        lenient().when(calcTaskMapper.update(any(), any())).thenReturn(1);
        lenient().when(calcTaskMapper.insert(any(com.hris.compensation.entity.CalcTask.class)))
                .thenAnswer(inv -> {
                    insertedTasks.add(inv.getArgument(0));
                    return 1;
                });
        // buildSummary 走 selectList：返回已插入任务（模拟同事务读已写）
        lenient().when(calcTaskMapper.selectList(any()))
                .thenAnswer(inv -> new java.util.ArrayList<>(insertedTasks));
    }

    @Nested
    @DisplayName("执行守卫")
    class Guards {

        @Test
        @DisplayName("REVIEW 批次 → CMP_ERROR 拒绝计算")
        void rejectsReviewState() {
            when(runMapper.selectById(RUN_ID)).thenReturn(run("REVIEW"));

            BizException e = assertThrows(BizException.class, () -> service.calcRun(RUN_ID));

            assertEquals(ErrorCode.CMP_ERROR.getCode(), e.getCode());
            verify(periodLockService, never()).ensurePeriodUnlocked(any());
        }

        @Test
        @DisplayName("CALCULATING 批次重复触发 → PARAM_INVALID「计算进行中」")
        void rejectsDuplicateTriggerWhileCalculating() {
            when(runMapper.selectById(RUN_ID)).thenReturn(run("CALCULATING"));

            BizException e = assertThrows(BizException.class, () -> service.calcRunAsync(RUN_ID));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            assertTrue(e.getMessage().contains("计算进行中"));
            verify(periodLockService, never()).ensurePeriodUnlocked(any());
        }

        @Test
        @DisplayName("期间已关账 → 守卫异常穿透，不落任务行")
        void rejectsLockedPeriod() {
            when(runMapper.selectById(RUN_ID)).thenReturn(run("INIT"));
            org.mockito.Mockito.doThrow(new BizException(ErrorCode.PARAM_INVALID, "期间已关账"))
                    .when(periodLockService).ensurePeriodUnlocked("2026-01");

            assertThrows(BizException.class, () -> service.calcRun(RUN_ID));

            verify(calcTaskMapper, never()).insert(any(com.hris.compensation.entity.CalcTask.class));
        }

        @Test
        @DisplayName("run 不存在 → NOT_FOUND")
        void rejectsMissingRun() {
            when(runMapper.selectById(RUN_ID)).thenReturn(null);
            assertThrows(BizException.class, () -> service.calcRun(RUN_ID));
        }
    }

    @Nested
    @DisplayName("异步算薪（批 8 #18a）")
    class AsyncCalc {

        @Test
        @DisplayName("异步触发：run CAS → CALCULATING + 提交专用线程池 + 透传租户上下文")
        void asyncFlow() {
            when(runMapper.selectById(RUN_ID)).thenReturn(run("INIT"));
            when(runMapper.update(any(), any())).thenReturn(1);

            var resp = service.calcRunAsync(RUN_ID);

            assertEquals("CALCULATING", resp.status());
            verify(runMapper).update(eq(null), any());
            verify(asyncRunner).run(eq(RUN_ID), eq("INIT"), any(), any(), any());
        }

        @Test
        @DisplayName("队列满（TaskRejected）→ run 回滚原状态 + CMP_ERROR")
        void queueFullRollsBack() {
            when(runMapper.selectById(RUN_ID)).thenReturn(run("INIT"));
            when(runMapper.update(any(), any())).thenReturn(1);
            org.mockito.Mockito.doThrow(new org.springframework.core.task.TaskRejectedException("full"))
                    .when(asyncRunner).run(anyLong(), anyString(), any(), any(), any());

            BizException e = assertThrows(BizException.class, () -> service.calcRunAsync(RUN_ID));

            assertEquals(ErrorCode.CMP_ERROR.getCode(), e.getCode());
            assertTrue(e.getMessage().contains("队列已满"));
            // 两次 update：CALCULATING CAS + 回滚 INIT（均经 update(null, wrapper)）
            verify(runMapper, org.mockito.Mockito.times(2)).update(eq(null), any());
        }

        @Test
        @DisplayName("异步执行体成功：整批计算后 run 流转 CALC")
        void completeCalcSuccess() {
            when(runMapper.selectById(RUN_ID)).thenReturn(run("CALCULATING"));
            when(payItemMapper.selectList(any())).thenReturn(List.of(
                    item("baseSalary", "EARNING", 10, "base")));
            when(employeePayMapper.selectList(any())).thenReturn(List.of(empPay(1L, "10000")));
            lenient().when(attendanceFetcher.fetch(anyList(), any(), any()))
                    .thenReturn(new AttendanceBriefFetcher.BriefResult(java.util.Map.of(), false));
            stubTaskAbsent();
            when(detailMapper.selectOne(any())).thenReturn(null);
            when(runMapper.updateById(any(com.hris.compensation.entity.PayrollRun.class))).thenReturn(1);

            service.completeCalc(RUN_ID, "INIT");

            ArgumentCaptor<com.hris.compensation.entity.PayrollRun> captor =
                    ArgumentCaptor.forClass(com.hris.compensation.entity.PayrollRun.class);
            verify(runMapper).updateById(captor.capture());
            assertEquals("CALC", captor.getValue().getStatus());
        }

        @Test
        @DisplayName("异步执行体整批异常：run 回滚提交前状态，不外抛")
        void completeCalcFailureRestoresStatus() {
            when(runMapper.selectById(RUN_ID)).thenReturn(run("CALCULATING"));
            when(payItemMapper.selectList(any())).thenThrow(new RuntimeException("db down"));

            service.completeCalc(RUN_ID, "TRIAL");

            verify(runMapper).update(eq(null), any());
            verify(runMapper, never()).updateById(any(com.hris.compensation.entity.PayrollRun.class));
        }
    }

    @Nested
    @DisplayName("单人重算（F-006）")
    class Retry {

        @Test
        @DisplayName("FAILED 任务可重试，成功后转 SUCCESS")
        void retryFailedTask() {
            when(runMapper.selectById(RUN_ID)).thenReturn(run("CALC"));
            var task = new com.hris.compensation.entity.CalcTask();
            task.setRunId(RUN_ID);
            task.setEmpId(1L);
            task.setStatus("FAILED");
            when(calcTaskMapper.selectOne(any())).thenReturn(task);
            when(payItemMapper.selectList(any())).thenReturn(List.of(
                    item("baseSalary", "EARNING", 10, "base")));
            when(employeePayMapper.selectList(any())).thenReturn(List.of(empPay(1L, "10000")));
            lenient().when(attendanceFetcher.fetch(anyList(), any(), any()))
                    .thenReturn(new AttendanceBriefFetcher.BriefResult(java.util.Map.of(), false));
            when(calcTaskMapper.update(any(), any())).thenReturn(1);
            when(detailMapper.selectOne(any())).thenReturn(null);

            var resp = service.retry(RUN_ID, 1L);

            assertEquals("SUCCESS", resp.status());
            assertTrue(resp.itemsJson().contains("10000"));
        }

        @Test
        @DisplayName("SUCCESS 任务重试 → CMP_ERROR 拒绝")
        void rejectsSuccessRetry() {
            when(runMapper.selectById(RUN_ID)).thenReturn(run("CALC"));
            var task = new com.hris.compensation.entity.CalcTask();
            task.setStatus("SUCCESS");
            when(calcTaskMapper.selectOne(any())).thenReturn(task);

            BizException e = assertThrows(BizException.class, () -> service.retry(RUN_ID, 1L));

            assertEquals(ErrorCode.CMP_ERROR.getCode(), e.getCode());
        }
    }
}

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 算薪整批计算与考勤变量注入单测（CMP-F-005/006 + 批 6 F-010）：
 * 成功路径、失败隔离、缺省 NET 公式、无定薪跳过、att 变量真实值/降级。
 *
 * <p>从 PayrollCalcServiceTest 拆分而来（单文件 ≤300 行门禁），夹具与原类保持一致。</p>
 */
@ExtendWith(MockitoExtension.class)
class PayrollCalcBatchCalcTest {

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

    private AttendanceBriefFetcher.BriefResult brief(long empId, long late, long absent) {
        return new AttendanceBriefFetcher.BriefResult(
                java.util.Map.of(empId, new com.hris.compensation.dto.AttendanceContractDtos
                        .AttendanceBriefRow(empId, late, absent)), false);
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
    @DisplayName("整批计算")
    class CalcAll {

        @Test
        @DisplayName("成功路径：逐员工求值 + run 状态迁移 CALC")
        void calcAllSuccess() {
            when(runMapper.selectById(RUN_ID)).thenReturn(run("INIT"));
            when(payItemMapper.selectList(any())).thenReturn(List.of(
                    item("baseSalary", "EARNING", 10, "base"),
                    item("net", "NET", 90, "baseSalary*2")));
            when(employeePayMapper.selectList(any())).thenReturn(List.of(
                    empPay(1L, "10000"), empPay(2L, "20000")));
            lenient().when(attendanceFetcher.fetch(anyList(), any(), any()))
                    .thenReturn(new AttendanceBriefFetcher.BriefResult(java.util.Map.of(), false));
            stubTaskAbsent();
            when(detailMapper.selectOne(any())).thenReturn(null);
            when(runMapper.updateById(any(com.hris.compensation.entity.PayrollRun.class))).thenReturn(1);

            var resp = service.calcRun(RUN_ID);

            assertEquals("CALC", resp.status());
            assertEquals(2, resp.total());
            assertEquals(2, resp.success());
            verify(detailMapper, org.mockito.Mockito.times(2))
                    .insert(any(com.hris.compensation.entity.PayrollDetail.class));
        }

        @Test
        @DisplayName("失败隔离：单员工公式异常 → 整批继续，任务行 FAILED 带原因")
        void failureIsolation() {
            when(runMapper.selectById(RUN_ID)).thenReturn(run("INIT"));
            when(payItemMapper.selectList(any())).thenReturn(List.of(
                    item("bad", "EARNING", 10, "ghostVar+1")));
            when(employeePayMapper.selectList(any())).thenReturn(List.of(
                    empPay(1L, "10000"), empPay(2L, "10000")));
            lenient().when(attendanceFetcher.fetch(anyList(), any(), any()))
                    .thenReturn(new AttendanceBriefFetcher.BriefResult(java.util.Map.of(), false));
            stubTaskAbsent();
            when(runMapper.updateById(any(com.hris.compensation.entity.PayrollRun.class))).thenReturn(1);

            var resp = service.calcRun(RUN_ID);

            assertEquals(2, resp.total());
            assertEquals(0, resp.success());
            assertEquals(2, resp.failed());
            verify(detailMapper, never()).insert(any(com.hris.compensation.entity.PayrollDetail.class));
        }

        @Test
        @DisplayName("NET 缺省公式：无 NET 项时自动补 sum(EARNING)-sum(DEDUCTION)-sum(TAX)")
        void defaultNetFormula() {
            when(runMapper.selectById(RUN_ID)).thenReturn(run("INIT"));
            when(payItemMapper.selectList(any())).thenReturn(List.of(
                    item("a", "EARNING", 10, "base"),
                    item("d", "DEDUCTION", 20, "100"),
                    item("t", "TAX", 30, "50")));
            when(employeePayMapper.selectList(any())).thenReturn(List.of(empPay(1L, "10000")));
            lenient().when(attendanceFetcher.fetch(anyList(), any(), any()))
                    .thenReturn(new AttendanceBriefFetcher.BriefResult(java.util.Map.of(), false));
            stubTaskAbsent();
            when(detailMapper.selectOne(any())).thenReturn(null);
            when(runMapper.updateById(any(com.hris.compensation.entity.PayrollRun.class))).thenReturn(1);

            service.calcRun(RUN_ID);

            // 应发 = 10000，实发 = 10000-100-50 = 9850
            verify(detailMapper).insert(org.mockito.ArgumentMatchers.argThat(
                    (com.hris.compensation.entity.PayrollDetail d) ->
                            "10000".equals(d.getAmountCipher()) && "9850".equals(d.getNetAmountCipher())));
        }

        @Test
        @DisplayName("无有效定薪 → 任务 SKIPPED，不中断整批")
        void skipWithoutSalary() {
            when(runMapper.selectById(RUN_ID)).thenReturn(run("INIT"));
            when(payItemMapper.selectList(any())).thenReturn(List.of());
            when(employeePayMapper.selectList(any())).thenReturn(List.of(
                    empPay(1L, null)));
            lenient().when(attendanceFetcher.fetch(anyList(), any(), any()))
                    .thenReturn(new AttendanceBriefFetcher.BriefResult(java.util.Map.of(), false));
            stubTaskAbsent();
            when(runMapper.updateById(any(com.hris.compensation.entity.PayrollRun.class))).thenReturn(1);

            var resp = service.calcRun(RUN_ID);

            assertEquals(1, resp.skipped());
        }
    }

    @Nested
    @DisplayName("F-010 考勤变量 live 接入")
    class AttendanceVariables {

        @Test
        @DisplayName("att 简报真实值进公式：lateCount/absentDays 注入求值结果")
        void realValuesIntoFormula() {
            when(runMapper.selectById(RUN_ID)).thenReturn(run("INIT"));
            when(payItemMapper.selectList(any())).thenReturn(List.of(
                    item("penalty", "EARNING", 10, "lateCount*50+absentDays*200")));
            when(employeePayMapper.selectList(any())).thenReturn(List.of(empPay(1L, "10000")));
            when(attendanceFetcher.fetch(anyList(), any(), any())).thenReturn(brief(1L, 2, 1));
            stubTaskAbsent();
            when(detailMapper.selectOne(any())).thenReturn(null);
            when(runMapper.updateById(any(com.hris.compensation.entity.PayrollRun.class))).thenReturn(1);

            service.calcRun(RUN_ID);

            // 2*50 + 1*200 = 300（应发口径：EARNING 项进 amount）
            verify(detailMapper).insert(org.mockito.ArgumentMatchers.argThat(
                    (com.hris.compensation.entity.PayrollDetail d) ->
                            "300".equals(d.getAmountCipher())));
            verify(attendanceFetcher).fetch(anyList(),
                    eq(java.time.LocalDate.of(2026, 1, 1)),
                    eq(java.time.LocalDate.of(2026, 1, 31)));
        }

        @Test
        @DisplayName("att 不可用 fail-open：变量降级 0 + 任务行 items_json 标注 fallback")
        void fallbackOnAttUnavailable() {
            when(runMapper.selectById(RUN_ID)).thenReturn(run("INIT"));
            when(payItemMapper.selectList(any())).thenReturn(List.of(
                    item("penalty", "EARNING", 10, "lateCount*50+absentDays*200")));
            when(employeePayMapper.selectList(any())).thenReturn(List.of(empPay(1L, "10000")));
            when(attendanceFetcher.fetch(anyList(), any(), any()))
                    .thenReturn(new AttendanceBriefFetcher.BriefResult(java.util.Map.of(), true));
            stubTaskAbsent();
            when(detailMapper.selectOne(any())).thenReturn(null);
            when(runMapper.updateById(any(com.hris.compensation.entity.PayrollRun.class))).thenReturn(1);

            service.calcRun(RUN_ID);

            // 降级 0：0*50+0*200 = 0
            verify(detailMapper).insert(org.mockito.ArgumentMatchers.argThat(
                    (com.hris.compensation.entity.PayrollDetail d) ->
                            "0".equals(d.getAmountCipher())));
            ArgumentCaptor<com.hris.compensation.entity.CalcTask> captor =
                    ArgumentCaptor.forClass(com.hris.compensation.entity.CalcTask.class);
            verify(calcTaskMapper).insert(captor.capture());
            assertTrue(captor.getValue().getItemsJson().contains("\"attendanceSource\":\"fallback\""));
        }
    }
}

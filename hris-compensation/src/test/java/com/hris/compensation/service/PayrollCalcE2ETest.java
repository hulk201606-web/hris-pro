package com.hris.compensation.service;

import com.hris.compensation.dto.CalcTaskDtos.CalcProgressResponse;
import com.hris.compensation.dto.CalcTaskDtos.CalcRunResponse;
import com.hris.compensation.dto.CalcTaskDtos.CalcTaskResponse;
import com.hris.compensation.dto.PayItemDtos.PayItemCreateRequest;
import com.hris.compensation.dto.PayItemDtos.PayItemResponse;
import com.hris.compensation.dto.PayItemDtos.PayItemUpdateRequest;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunCreateRequest;
import com.hris.compensation.entity.CalcTask;
import com.hris.compensation.entity.EmployeePay;
import com.hris.compensation.entity.PayGroup;
import com.hris.compensation.entity.PayrollDetail;
import com.hris.compensation.mapper.CalcTaskMapper;
import com.hris.compensation.mapper.EmployeePayMapper;
import com.hris.compensation.mapper.PayGroupMapper;
import com.hris.compensation.mapper.PayItemMapper;
import com.hris.compensation.mapper.PayrollDetailMapper;
import com.hris.compensation.mapper.PayrollRunMapper;
import com.hris.common.trace.TraceContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 算薪引擎 E2E（CMP-F-005/006/021，真跑本地 MySQL cmp_db）。
 *
 * <p>链路：建薪资组 → 2 员工定薪 → 配公式 → calculate → 断言明细金额与任务行 →
 * 注入坏公式重算（失败定位）→ 修公式后单人重算成功。</p>
 *
 * <p>需本地 MySQL（127.0.0.1:3306/cmp_db）与 DB_PASSWORD 环境变量；
 * 未提供 DB_PASSWORD 时整类跳过（CI 无库环境不阻塞）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.cloud.nacos.discovery.enabled=false"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class PayrollCalcE2ETest {

    @Autowired PayrollRunService runService;
    @Autowired PayrollCalcService calcService;
    @Autowired PayrollCalcTaskService taskService;
    @Autowired PayItemService payItemService;
    @Autowired PayGroupMapper payGroupMapper;
    @Autowired EmployeePayMapper employeePayMapper;
    @Autowired PayItemMapper payItemMapper;
    @Autowired PayrollRunMapper runMapper;
    @Autowired PayrollDetailMapper detailMapper;
    @Autowired CalcTaskMapper calcTaskMapper;

    static Long tenantId;
    static Long payGroupId;
    static Long emp1;
    static Long emp2;
    static Long runId;
    static Long insuranceItemId;

    @BeforeAll
    static void initContext() {
        tenantId = 981_000_000L + System.nanoTime() % 1_000_000L;
        emp1 = 901_000_000L + System.nanoTime() % 1_000_000L;
        emp2 = 902_000_000L + System.nanoTime() % 1_000_000L;
        TraceContext.setTenantId(tenantId);
        TraceContext.setUserId(1L);
    }

    @AfterAll
    static void cleanup(@Autowired PayGroupMapper pg, @Autowired EmployeePayMapper ep,
                        @Autowired PayItemMapper pi, @Autowired PayrollRunMapper pr,
                        @Autowired PayrollDetailMapper pd, @Autowired CalcTaskMapper ct) {
        // -De2e.keep=true 保留数据供人工查验（证据导出），需手工按 tenant_id 清理
        if (System.getProperty("e2e.keep") != null) {
            return;
        }
        if (runId != null) {
            ct.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CalcTask>()
                    .eq(CalcTask::getRunId, runId));
            pd.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayrollDetail>()
                    .eq(PayrollDetail::getRunId, runId));
            pr.deleteById(runId);
        }
        if (payGroupId != null) {
            pi.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.hris.compensation.entity.PayItem>()
                    .eq(com.hris.compensation.entity.PayItem::getPayGroupId, payGroupId));
            ep.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EmployeePay>()
                    .eq(EmployeePay::getPayGroupId, payGroupId));
            pg.deleteById(payGroupId);
        }
        TraceContext.clear();
    }

    @Test
    @Order(1)
    @DisplayName("建薪资组 + 2 员工定薪 + 配公式")
    void seedData() {
        PayGroup group = new PayGroup();
        group.setName("E2E 薪资组");
        group.setCode("E2E-" + tenantId);
        group.setPayCycle("MONTHLY");
        payGroupMapper.insert(group);
        payGroupId = group.getId();

        seedPay(emp1, "10000");
        seedPay(emp2, "20000");

        createItem("salary", "基本工资", "EARNING", 10, "base");
        createItem("bonus", "绩效", "EARNING", 20, "base*0.2");
        insuranceItemId = createItem("insurance", "社保", "DEDUCTION", 30, "round(salary*0.105,2)").id();
        createItem("tax", "个税", "TAX", 40, "taxMonthly(salary+bonus-insurance-5000)");
        createItem("net", "实发", "NET", 90, "salary+bonus-insurance-tax");

        runId = runService.create(new PayrollRunCreateRequest("2026-10", payGroupId, 1L, null)).id();
        assertTrue(runId > 0);
    }

    @Test
    @Order(2)
    @DisplayName("calculate：明细金额正确 + 任务行 SUCCESS")
    void calculateAndAssert() {
        CalcRunResponse resp = calcService.calcRun(runId);
        assertEquals("CALC", resp.status());
        assertEquals(2, resp.total());
        assertEquals(2, resp.success());

        List<PayrollDetail> details = detailMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayrollDetail>()
                        .eq(PayrollDetail::getRunId, runId).orderByAsc(PayrollDetail::getEmpId));
        assertEquals(2, details.size());
        // 员工1：应发=10000+2000=12000；社保=1050.00；个税=taxMonthly(5950)=385.00；实发=10565.00
        PayrollDetail d1 = details.get(0);
        assertEquals(0, new BigDecimal("12000.0").compareTo(new BigDecimal(d1.getAmountCipher())));
        assertEquals(0, new BigDecimal("10565.00").compareTo(new BigDecimal(d1.getNetAmountCipher())));
        // 员工2：应发=24000；社保=2100.00；个税=taxMonthly(16900)=1970.00；实发=19930.00
        PayrollDetail d2 = details.get(1);
        assertEquals(0, new BigDecimal("24000.0").compareTo(new BigDecimal(d2.getAmountCipher())));
        assertEquals(0, new BigDecimal("19930.00").compareTo(new BigDecimal(d2.getNetAmountCipher())));

        List<CalcTaskResponse> tasks = taskService.tasks(runId, null);
        assertEquals(2, tasks.size());
        assertTrue(tasks.stream().allMatch(t -> "SUCCESS".equals(t.status())));
        CalcProgressResponse progress = taskService.progress(runId);
        assertEquals(100, progress.progressPercent());
    }

    @Test
    @Order(3)
    @DisplayName("注入坏公式重算 → 失败定位到员工任务行")
    void recalcWithBrokenFormula() {
        payItemService.update(insuranceItemId,
                new PayItemUpdateRequest(null, null, null, "ghostVar+1", null));
        CalcRunResponse resp = calcService.calcRun(runId);
        assertEquals(2, resp.failed());

        List<CalcTaskResponse> failed = taskService.tasks(runId, "FAILED");
        assertEquals(2, failed.size());
        assertTrue(failed.stream().allMatch(t -> t.error() != null && t.error().contains("ghostVar")));
    }

    @Test
    @Order(4)
    @DisplayName("修公式后单人重算 → 逐人恢复 SUCCESS")
    void retryAfterFix() {
        payItemService.update(insuranceItemId,
                new PayItemUpdateRequest(null, null, null, "round(salary*0.105,2)", null));
        CalcTaskResponse r1 = calcService.retry(runId, emp1);
        assertEquals("SUCCESS", r1.status());
        // 未重算的员工仍是 FAILED（互不干扰）
        assertEquals("FAILED", taskService.tasks(runId, null).stream()
                .filter(t -> t.empId().equals(emp2)).findFirst().orElseThrow().status());
        CalcTaskResponse r2 = calcService.retry(runId, emp2);
        assertEquals("SUCCESS", r2.status());
        assertEquals(100, taskService.progress(runId).progressPercent());

        PayrollDetail d1 = detailMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayrollDetail>()
                        .eq(PayrollDetail::getRunId, runId).eq(PayrollDetail::getEmpId, emp1));
        assertEquals(0, new BigDecimal("10565.00").compareTo(new BigDecimal(d1.getNetAmountCipher())));
    }

    private void seedPay(Long empId, String amount) {
        EmployeePay pay = new EmployeePay();
        pay.setEmpId(empId);
        pay.setPayGroupId(payGroupId);
        pay.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        pay.setEffectiveTo(null);
        pay.setSalaryAmountCipher(amount);
        pay.setSalaryAmountMask("****");
        employeePayMapper.insert(pay);
    }

    private PayItemResponse createItem(String code, String name, String category, int seq, String formula) {
        return payItemService.create(new PayItemCreateRequest(
                payGroupId, name, code, category, seq, formula, 1));
    }
}

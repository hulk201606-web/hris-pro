package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.compensation.dto.CalcTaskDtos.CalcRunResponse;
import com.hris.compensation.dto.CalcTaskDtos.CalcTaskResponse;
import com.hris.compensation.entity.CalcTask;
import com.hris.compensation.entity.EmployeePay;
import com.hris.compensation.entity.PayItem;
import com.hris.compensation.entity.PayrollDetail;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.CalcTaskMapper;
import com.hris.compensation.mapper.EmployeePayMapper;
import com.hris.compensation.mapper.PayItemMapper;
import com.hris.compensation.mapper.PayrollDetailMapper;
import com.hris.compensation.mapper.PayrollRunMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 算薪引擎（CMP-F-005）+ 逐员工任务监控（CMP-F-006）+ 异步批算（批 8 #18a）。
 *
 * <p>执行流：run 状态守卫（INIT/TRIAL/CALC 可算，CALCULATING 中重复触发拒绝）→
 * 期间锁守卫 → 求值前经 att attendance-brief 批量拉取考勤变量（F-010，fail-open 降级 0
 * 并在任务行 items_json 标注 attendanceSource=fallback）→ 按 seq 顺序逐项求值
 * （变量 = base + lateCount/absentDays + priorTax + 前序项 code）→ 落 cmp_payroll_detail
 * （密文列照现有密域写法）+ 逐员工 cmp_calc_task 行。</p>
 *
 * <p>异步语义（批 8 #18a）：calculate 端点缺省异步——run.status 先行流转 CALCULATING
 * （CAS），再提交 payrollCalcExecutor（有界队列）；完成按任务行结果流转 CALC。
 * ?mode=sync 保留同步路径。失败隔离：单员工求值异常不中断整批；整批异常时 run 回滚至
 * 提交前状态。单人重算仅 FAILED/SKIPPED 可重试。</p>
 */
@Service
public class PayrollCalcService {

    private static final Logger log = LoggerFactory.getLogger(PayrollCalcService.class);

    /** 任务状态。 */
    public static final String TASK_SUCCESS = "SUCCESS";
    public static final String TASK_FAILED = "FAILED";
    public static final String TASK_SKIPPED = "SKIPPED";

    /** run 状态：异步计算中（批 8 #18a 引入的瞬时态，完成流转 CALC）。 */
    public static final String RUN_CALCULATING = "CALCULATING";

    private final PayrollRunMapper runMapper;
    private final PayrollDetailMapper detailMapper;
    private final EmployeePayMapper employeePayMapper;
    private final PayItemMapper payItemMapper;
    private final CalcTaskMapper calcTaskMapper;
    private final PeriodLockService periodLockService;
    private final FormulaSandboxService sandbox;
    private final PayItemEvaluator evaluator;
    private final CalcResultWriter writer;
    private final AttendanceBriefFetcher attendanceFetcher;
    private final PayrollCalcAsyncRunner asyncRunner;
    private final SalaryProrationService prorationService;
    private final CmpCalcConfigService calcConfigService;

    /** 仅用于序列化快照（writeValueAsString 线程安全），无反序列化配置需求。 */
    private static final ObjectMapper JSON = new ObjectMapper();

    public PayrollCalcService(PayrollRunMapper runMapper, PayrollDetailMapper detailMapper,
                              EmployeePayMapper employeePayMapper, PayItemMapper payItemMapper,
                              CalcTaskMapper calcTaskMapper, PeriodLockService periodLockService,
                              FormulaSandboxService sandbox, PayItemEvaluator evaluator,
                              CalcResultWriter writer, AttendanceBriefFetcher attendanceFetcher,
                              @Lazy PayrollCalcAsyncRunner asyncRunner,
                              SalaryProrationService prorationService,
                              CmpCalcConfigService calcConfigService) {
        this.runMapper = runMapper;
        this.detailMapper = detailMapper;
        this.employeePayMapper = employeePayMapper;
        this.payItemMapper = payItemMapper;
        this.calcTaskMapper = calcTaskMapper;
        this.periodLockService = periodLockService;
        this.sandbox = sandbox;
        this.evaluator = evaluator;
        this.writer = writer;
        this.attendanceFetcher = attendanceFetcher;
        this.asyncRunner = asyncRunner;
        this.prorationService = prorationService;
        this.calcConfigService = calcConfigService;
    }

    /** 触发整批算薪（同步路径，?mode=sync 兼容/测试用）。返回任务汇总与 run 最新状态。 */
    @Transactional(rollbackFor = Exception.class)
    public CalcRunResponse calcRun(Long runId) {
        PayrollRun run = requireRunnable(runId);
        periodLockService.ensurePeriodUnlocked(run.getPeriodCode());
        List<PayItem> items = PayrollCalcSupport.loadItems(payItemMapper, run.getPayGroupId());
        List<EmployeePay> empPays = PayrollCalcSupport.loadEffectiveEmpPays(employeePayMapper, run.getPayGroupId(), run.getPeriodCode());
        AttendanceBriefFetcher.BriefResult att =
                fetchAttendance(run.getPeriodCode(), empPays.stream().map(EmployeePay::getEmpId).toList());
        // CMP-F-011：租户开关「离职当日是否计薪」每批读取一次（配置中心 → 部署默认）
        boolean payTerminationDay = calcConfigService.payTerminationDay();
        for (EmployeePay empPay : empPays) {
            calcOne(run, items, empPay.getEmpId(), att, payTerminationDay);
        }
        run.setStatus("CALC");
        if (runMapper.updateById(run) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return buildSummary(runId, run.getStatus());
    }

    /**
     * 触发整批算薪（批 8 #18a，缺省异步）：run 状态 CAS → CALCULATING 后提交专用线程池；
     * 队列满（拒绝）时回滚状态并报错。返回 CALCULATING 快照，进度经 calc-progress 轮询。
     */
    public CalcRunResponse calcRunAsync(Long runId) {
        PayrollRun run = requireRunnable(runId);
        periodLockService.ensurePeriodUnlocked(run.getPeriodCode());
        String prevStatus = run.getStatus();
        int updated = runMapper.update(null, new LambdaUpdateWrapper<PayrollRun>()
                .eq(PayrollRun::getId, runId)
                .eq(PayrollRun::getStatus, prevStatus)
                .set(PayrollRun::getStatus, RUN_CALCULATING));
        if (updated == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        try {
            asyncRunner.run(runId, prevStatus, TraceContext.getTenantId(),
                    TraceContext.getTraceId(), TraceContext.getUserId());
        } catch (org.springframework.core.task.TaskRejectedException e) {
            restoreStatus(runId, prevStatus);
            log.warn("算薪队列已满，run 回滚状态 {}: runId={}", prevStatus, runId);
            throw new BizException(ErrorCode.CMP_ERROR, "计算队列已满，请稍后重试");
        }
        return new CalcRunResponse(runId, RUN_CALCULATING, 0, 0, 0, 0);
    }

    /** 异步执行体（@Async 线程池内调用）：整批计算 + 状态流转；整批异常回滚 run 状态。 */
    public void completeCalc(Long runId, String prevStatus) {
        try {
            PayrollRun run = runMapper.selectById(runId);
            if (run == null) {
                log.warn("异步算薪中止：run 已不存在 runId={}", runId);
                return;
            }
            List<PayItem> items = PayrollCalcSupport.loadItems(payItemMapper, run.getPayGroupId());
            List<EmployeePay> empPays =
                    PayrollCalcSupport.loadEffectiveEmpPays(employeePayMapper, run.getPayGroupId(), run.getPeriodCode());
            AttendanceBriefFetcher.BriefResult att =
                    fetchAttendance(run.getPeriodCode(), empPays.stream().map(EmployeePay::getEmpId).toList());
            // CMP-F-011：与同步路径同口径，异步批亦按租户开关折算
            boolean payTerminationDay = calcConfigService.payTerminationDay();
            for (EmployeePay empPay : empPays) {
                calcOne(run, items, empPay.getEmpId(), att, payTerminationDay);
            }
            run.setStatus("CALC");
            if (runMapper.updateById(run) == 0) {
                throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
            }
        } catch (Exception e) {
            log.error("异步算薪失败，run 回滚状态 {}: runId={}", prevStatus, runId, e);
            restoreStatus(runId, prevStatus);
        }
    }

    /** 单人重算（F-006）：仅 FAILED/SKIPPED 任务可重试，成功后改 SUCCESS。 */
    @Transactional(rollbackFor = Exception.class)
    public CalcTaskResponse retry(Long runId, Long empId) {
        PayrollRun run = requireRunnable(runId);
        periodLockService.ensurePeriodUnlocked(run.getPeriodCode());
        CalcTask task = calcTaskMapper.selectOne(new LambdaQueryWrapper<CalcTask>()
                .eq(CalcTask::getRunId, runId).eq(CalcTask::getEmpId, empId));
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "算薪任务不存在");
        }
        if (!TASK_FAILED.equals(task.getStatus()) && !TASK_SKIPPED.equals(task.getStatus())) {
            throw new BizException(ErrorCode.CMP_ERROR,
                    "仅失败或跳过的任务可重试（当前 " + task.getStatus() + "）");
        }
        List<PayItem> items = PayrollCalcSupport.loadItems(payItemMapper, run.getPayGroupId());
        EmployeePay empPay = PayrollCalcSupport.requireEffectiveEmpPay(employeePayMapper, run.getPayGroupId(), run.getPeriodCode(), empId);
        AttendanceBriefFetcher.BriefResult att = fetchAttendance(run.getPeriodCode(), List.of(empId));
        return toTaskResponse(calcOne(run, items, empId, empPay, att,
                calcConfigService.payTerminationDay()));
    }

    /** 求值前批量拉取考勤简报（F-010）：periodCode → 自然月区间，fail-open。 */
    private AttendanceBriefFetcher.BriefResult fetchAttendance(String periodCode, List<Long> empIds) {
        YearMonth ym = YearMonth.parse(periodCode);
        return attendanceFetcher.fetch(empIds, ym.atDay(1), ym.atEndOfMonth());
    }

    /** 单员工求值 + 落明细 + 落任务行（失败不抛出，返回 FAILED 任务行）。 */
    private CalcTask calcOne(PayrollRun run, List<PayItem> items, Long empId,
                             AttendanceBriefFetcher.BriefResult att, boolean payTerminationDay) {
        EmployeePay empPay;
        try {
            empPay = PayrollCalcSupport.requireEffectiveEmpPay(employeePayMapper, run.getPayGroupId(), run.getPeriodCode(), empId);
        } catch (BizException e) {
            return writer.upsertTask(run.getId(), empId, TASK_SKIPPED, e.getMessage(), null);
        }
        return calcOne(run, items, empId, empPay, att, payTerminationDay);
    }

    private CalcTask calcOne(PayrollRun run, List<PayItem> items, Long empId, EmployeePay empPay,
                             AttendanceBriefFetcher.BriefResult att, boolean payTerminationDay) {
        if (empPay.getSalaryAmountCipher() == null || empPay.getSalaryAmountCipher().isBlank()) {
            // 无定薪金额：跳过而非失败（F-006 SKIPPED 语义）
            return writer.upsertTask(run.getId(), empId, TASK_SKIPPED, "无有效定薪金额", null);
        }
        try {
            BigDecimal rawBase = PayrollCalcSupport.parseAmount(empPay.getSalaryAmountCipher(),
                    "员工 " + empId + " 定薪金额无效");
            // CMP-F-011：入离职当日折算——入职当日恒计，离职当日由租户开关决定；
            // 折算窗口取定薪生效区间（cmp_employee_pay.effective_from/to），整月覆盖时原值直出。
            BigDecimal base = prorationService.prorate(rawBase, empPay.getEffectiveFrom(),
                    empPay.getEffectiveTo(), YearMonth.parse(run.getPeriodCode()), payTerminationDay);
            Map<String, Object> results = evaluator.evalItems(items, base, run.getPeriodCode(),
                    AttendanceBriefFetcher.lateCount(att, empId),
                    AttendanceBriefFetcher.absentDays(att, empId));
            if (att.fallback()) {
                // F-010 降级标注：随任务行快照可审计（密文/快照语义照现有）
                results.put("attendanceSource", "fallback");
            }
            writer.upsertDetail(run, empId, base, items, results);
            return writer.upsertTask(run.getId(), empId, TASK_SUCCESS, null,
                    JSON.writeValueAsString(results));
        } catch (Exception e) {
            log.warn("算薪失败: runId={}, empId={}", run.getId(), empId, e);
            return writer.upsertTask(run.getId(), empId, TASK_FAILED, PayrollCalcSupport.truncate(e.getMessage()), null);
        }
    }

    /** 异常回滚 run 状态（CALCULATING → prev，CAS 防覆盖）。 */
    private void restoreStatus(Long runId, String prevStatus) {
        try {
            runMapper.update(null, new LambdaUpdateWrapper<PayrollRun>()
                    .eq(PayrollRun::getId, runId)
                    .eq(PayrollRun::getStatus, RUN_CALCULATING)
                    .set(PayrollRun::getStatus, prevStatus));
        } catch (Exception ex) {
            log.error("run 状态回滚失败 runId={}: {}", runId, ex.getMessage());
        }
    }

    /** 薪资组启用项（含全局 0=全局项），seq 升序；NET 缺失时自动补缺省项。 */
    private PayrollRun requireRunnable(Long runId) {
        PayrollRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
        String status = run.getStatus();
        if (RUN_CALCULATING.equals(status)) {
            // 幂等（批 8 #18a）：计算进行中拒绝重复触发
            throw new BizException(ErrorCode.PARAM_INVALID, "计算进行中");
        }
        if (!"INIT".equals(status) && !"TRIAL".equals(status) && !"CALC".equals(status)) {
            throw new BizException(ErrorCode.CMP_ERROR,
                    "当前状态不可算薪（" + status + "），仅 INIT/TRIAL/CALC 可执行");
        }
        return run;
    }

    private CalcRunResponse buildSummary(Long runId, String status) {
        List<CalcTask> tasks = calcTaskMapper.selectList(new LambdaQueryWrapper<CalcTask>()
                .eq(CalcTask::getRunId, runId));
        long success = countByStatus(tasks, TASK_SUCCESS);
        return new CalcRunResponse(runId, status, tasks.size(), success,
                countByStatus(tasks, TASK_FAILED), countByStatus(tasks, TASK_SKIPPED));
    }

    private long countByStatus(List<CalcTask> tasks, String status) {
        return tasks.stream().filter(t -> status.equals(t.getStatus())).count();
    }

    private CalcTaskResponse toTaskResponse(CalcTask t) {
        return new CalcTaskResponse(t.getId(), t.getRunId(), t.getEmpId(), t.getStatus(),
                t.getError(), t.getItemsJson(), t.getCreatedAt(), t.getUpdatedAt());
    }

}

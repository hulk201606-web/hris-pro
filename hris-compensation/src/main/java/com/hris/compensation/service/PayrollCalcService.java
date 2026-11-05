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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 算薪引擎（CMP-F-005）+ 逐员工任务监控（CMP-F-006）。
 *
 * <p>执行流：run 状态守卫（INIT/TRIAL/CALC 可算）→ 期间锁守卫（四路径外的新计算路径
 * 同样过 ensurePeriodUnlocked）→ 遍历薪资组现行有效定薪员工 → 按 seq 顺序逐项求值
 * （变量 = base + lateCount/absentDays 占位 + 前序项 code）→ 落 cmp_payroll_detail
 * （密文列照现有密域写法，密文列存金额、通用响应仅掩码）+ 逐员工 cmp_calc_task 行。</p>
 *
 * <p>失败隔离：单员工求值异常不中断整批，任务行记 FAILED + error 原因；单人重算
 * 仅 FAILED/SKIPPED 可重试。</p>
 *
 * <p>TODO: 同步执行 MVP，大批量异步化（RocketMQ 分批并行）见 docs/20 §6 批 3 遗留。</p>
 */
@Service
public class PayrollCalcService {

    private static final Logger log = LoggerFactory.getLogger(PayrollCalcService.class);

    /** 任务状态。 */
    public static final String TASK_SUCCESS = "SUCCESS";
    public static final String TASK_FAILED = "FAILED";
    public static final String TASK_SKIPPED = "SKIPPED";

    private final PayrollRunMapper runMapper;
    private final PayrollDetailMapper detailMapper;
    private final EmployeePayMapper employeePayMapper;
    private final PayItemMapper payItemMapper;
    private final CalcTaskMapper calcTaskMapper;
    private final PeriodLockService periodLockService;
    private final FormulaSandboxService sandbox;
    private final PayItemEvaluator evaluator;
    private final CalcResultWriter writer;

    /** 仅用于序列化快照（writeValueAsString 线程安全），无反序列化配置需求。 */
    private static final ObjectMapper JSON = new ObjectMapper();

    public PayrollCalcService(PayrollRunMapper runMapper, PayrollDetailMapper detailMapper,
                              EmployeePayMapper employeePayMapper, PayItemMapper payItemMapper,
                              CalcTaskMapper calcTaskMapper, PeriodLockService periodLockService,
                              FormulaSandboxService sandbox, PayItemEvaluator evaluator,
                              CalcResultWriter writer) {
        this.runMapper = runMapper;
        this.detailMapper = detailMapper;
        this.employeePayMapper = employeePayMapper;
        this.payItemMapper = payItemMapper;
        this.calcTaskMapper = calcTaskMapper;
        this.periodLockService = periodLockService;
        this.sandbox = sandbox;
        this.evaluator = evaluator;
        this.writer = writer;
    }

    /** 触发整批算薪（同步 MVP）。返回任务汇总与 run 最新状态。 */
    @Transactional(rollbackFor = Exception.class)
    public CalcRunResponse calcRun(Long runId) {
        PayrollRun run = requireRunnable(runId);
        periodLockService.ensurePeriodUnlocked(run.getPeriodCode());
        List<PayItem> items = loadItems(run.getPayGroupId());
        List<EmployeePay> empPays = loadEffectiveEmpPays(run.getPayGroupId(), run.getPeriodCode());
        for (EmployeePay empPay : empPays) {
            calcOne(run, items, empPay.getEmpId());
        }
        run.setStatus("CALC");
        if (runMapper.updateById(run) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return buildSummary(runId, run.getStatus());
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
        List<PayItem> items = loadItems(run.getPayGroupId());
        EmployeePay empPay = requireEffectiveEmpPay(run.getPayGroupId(), run.getPeriodCode(), empId);
        return toTaskResponse(calcOne(run, items, empId, empPay));
    }

    /** 单员工求值 + 落明细 + 落任务行（失败不抛出，返回 FAILED 任务行）。 */
    private CalcTask calcOne(PayrollRun run, List<PayItem> items, Long empId) {
        EmployeePay empPay;
        try {
            empPay = requireEffectiveEmpPay(run.getPayGroupId(), run.getPeriodCode(), empId);
        } catch (BizException e) {
            return writer.upsertTask(run.getId(), empId, TASK_SKIPPED, e.getMessage(), null);
        }
        return calcOne(run, items, empId, empPay);
    }

    private CalcTask calcOne(PayrollRun run, List<PayItem> items, Long empId, EmployeePay empPay) {
        if (empPay.getSalaryAmountCipher() == null || empPay.getSalaryAmountCipher().isBlank()) {
            // 无定薪金额：跳过而非失败（F-006 SKIPPED 语义）
            return writer.upsertTask(run.getId(), empId, TASK_SKIPPED, "无有效定薪金额", null);
        }
        try {
            BigDecimal base = parseAmount(empPay.getSalaryAmountCipher(),
                    "员工 " + empId + " 定薪金额无效");
            Map<String, Object> results = evaluator.evalItems(items, base, run.getPeriodCode());
            writer.upsertDetail(run, empId, base, items, results);
            return writer.upsertTask(run.getId(), empId, TASK_SUCCESS, null,
                    JSON.writeValueAsString(results));
        } catch (Exception e) {
            log.warn("算薪失败: runId={}, empId={}", run.getId(), empId, e);
            return writer.upsertTask(run.getId(), empId, TASK_FAILED, truncate(e.getMessage()), null);
        }
    }

    /** 薪资组启用项（含全局 0=全局项），seq 升序；NET 缺失时自动补缺省项。 */
    private List<PayItem> loadItems(Long payGroupId) {
        List<PayItem> items = payItemMapper.selectList(new LambdaQueryWrapper<PayItem>()
                .in(PayItem::getPayGroupId, List.of(payGroupId, 0L))
                .eq(PayItem::getStatus, 1)
                .orderByAsc(PayItem::getSeq).orderByAsc(PayItem::getId));
        boolean hasNet = items.stream().anyMatch(i -> PayItem.CATEGORY_NET.equals(i.getCategory()));
        if (!hasNet) {
            PayItem net = new PayItem();
            net.setCode("netPay");
            net.setName("实发(缺省)");
            net.setCategory(PayItem.CATEGORY_NET);
            net.setSeq(Integer.MAX_VALUE);
            items = new ArrayList<>(items);
            items.add(net);
        }
        return items;
    }

    /** 现行有效定薪（effective_from <= 期间月末 且 effective_to 为空或 >= 期间月初）。 */
    private List<EmployeePay> loadEffectiveEmpPays(Long payGroupId, String periodCode) {
        YearMonth ym = YearMonth.parse(periodCode);
        LocalDate periodStart = ym.atDay(1);
        LocalDate periodEnd = ym.atEndOfMonth();
        return employeePayMapper.selectList(new LambdaQueryWrapper<EmployeePay>()
                .eq(EmployeePay::getPayGroupId, payGroupId)
                .le(EmployeePay::getEffectiveFrom, periodEnd)
                .and(w -> w.isNull(EmployeePay::getEffectiveTo)
                        .or().ge(EmployeePay::getEffectiveTo, periodStart)));
    }

    private EmployeePay requireEffectiveEmpPay(Long payGroupId, String periodCode, Long empId) {
        List<EmployeePay> pays = loadEffectiveEmpPays(payGroupId, periodCode).stream()
                .filter(p -> p.getEmpId().equals(empId)).toList();
        if (pays.isEmpty()) {
            throw new BizException(ErrorCode.CMP_ERROR, "员工 " + empId + " 无现行有效定薪");
        }
        return pays.get(0);
    }

    private PayrollRun requireRunnable(Long runId) {
        PayrollRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
        String status = run.getStatus();
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

    private BigDecimal parseAmount(String cipher, String errMsg) {
        if (cipher == null || cipher.isBlank()) {
            throw new BizException(ErrorCode.CMP_ERROR, errMsg);
        }
        try {
            return new BigDecimal(cipher.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.CMP_ERROR, errMsg);
        }
    }

    private String truncate(String msg) {
        if (msg == null || msg.isBlank()) {
            return "算薪失败";
        }
        return msg.length() > 255 ? msg.substring(0, 255) : msg;
    }
}

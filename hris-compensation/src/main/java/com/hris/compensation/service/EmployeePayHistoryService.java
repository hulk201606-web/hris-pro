package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
import com.hris.compensation.dto.EmployeePayHistoryDtos.EmployeePayHistoryItem;
import com.hris.compensation.entity.EmployeePay;
import com.hris.compensation.entity.PayrollAdjustment;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.EmployeePayMapper;
import com.hris.compensation.mapper.PayrollAdjustmentMapper;
import com.hris.compensation.mapper.PayrollRunMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 调薪轨迹查询（CMP-F-018）。
 *
 * <p>数据源与覆盖度（如实标注）：
 * <ol>
 *   <li>主源 cmp_employee_pay 版本链：每行一个生效区间，effectiveDate=effective_from，
 *       baseAmount=salary_amount（密文列经 TypeHandler 读时解密，业务层明文）。</li>
 *   <li>补充源 cmp_payroll_adjustment：仅当该变更月份在版本链中缺失时拼接，effectiveDate
 *       取所属批次 periodCode 的月初，baseAmount=调整金额（补款/扣款额，非定薪额），
 *       reason 前缀 adjustType 以便区分。</li>
 *   <li>approvalId：当前 cmp_employee_pay / cmp_payroll_adjustment 均无审批单关联列，
 *       恒为 null（审批单反查依赖后续批次补列或 flow 侧 bizKey 台账）。</li>
 * </ol></p>
 *
 * <p>权限：金额属薪酬密域，除控制器 cmp:employee-pay:read 外，服务层再校验
 * cmp:salary:read，缺失抛 CMP_SALARY_FORBIDDEN，防内部路径绕过切面。</p>
 */
@Service
public class EmployeePayHistoryService {

    private static final Logger log = LoggerFactory.getLogger(EmployeePayHistoryService.class);

    /** 密域读权限点（与 SalarySecretService 同源）。 */
    public static final String PERM_SALARY_READ = "cmp:salary:read";

    /** 轨迹来源：定薪版本链。 */
    public static final String SOURCE_PAY_VERSION = "PAY_VERSION";
    /** 轨迹来源：算薪调整单（补差）。 */
    public static final String SOURCE_ADJUSTMENT = "ADJUSTMENT";

    private final EmployeePayMapper employeePayMapper;
    private final PayrollAdjustmentMapper adjustmentMapper;
    private final PayrollRunMapper runMapper;

    public EmployeePayHistoryService(EmployeePayMapper employeePayMapper,
                                     PayrollAdjustmentMapper adjustmentMapper,
                                     PayrollRunMapper runMapper) {
        this.employeePayMapper = employeePayMapper;
        this.adjustmentMapper = adjustmentMapper;
        this.runMapper = runMapper;
    }

    /**
     * 按员工查询调薪轨迹，effectiveDate 倒序（同日按 createdAt 倒序）。
     */
    public List<EmployeePayHistoryItem> history(Long empId) {
        requireSalaryPerm();
        if (empId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "empId 不能为空");
        }
        List<EmployeePay> versions = employeePayMapper.selectList(new LambdaQueryWrapper<EmployeePay>()
                .eq(EmployeePay::getEmpId, empId)
                .orderByDesc(EmployeePay::getEffectiveFrom)
                .orderByDesc(EmployeePay::getId));
        List<PayrollAdjustment> adjustments = adjustmentMapper.selectList(
                new LambdaQueryWrapper<PayrollAdjustment>().eq(PayrollAdjustment::getEmpId, empId));
        Map<Long, String> periodByRun = runPeriods(adjustments);
        Map<Long, String> adjustReasons = adjustmentReasons(adjustments, periodByRun);

        List<EmployeePayHistoryItem> items = new ArrayList<>();
        Set<YearMonth> coveredMonths = new HashSet<>();
        for (EmployeePay v : versions) {
            if (v.getEffectiveFrom() == null) {
                continue;
            }
            YearMonth ym = YearMonth.from(v.getEffectiveFrom());
            coveredMonths.add(ym);
            items.add(new EmployeePayHistoryItem(v.getEffectiveFrom(), parse(v.getSalaryAmountCipher()),
                    adjustReasons.get(v.getEffectiveFrom().toEpochDay()), null,
                    v.getCreatedBy(), v.getCreatedAt(), SOURCE_PAY_VERSION));
        }
        items.addAll(adjustmentItems(adjustments, periodByRun, coveredMonths));
        items.sort(Comparator
                .comparing(EmployeePayHistoryItem::effectiveDate,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(EmployeePayHistoryItem::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    /** 缺失月份的调整单补充（批次 periodCode 月初为生效日）。 */
    private List<EmployeePayHistoryItem> adjustmentItems(List<PayrollAdjustment> adjustments,
                                                         Map<Long, String> periodByRun,
                                                         Set<YearMonth> coveredMonths) {
        List<EmployeePayHistoryItem> items = new ArrayList<>();
        for (PayrollAdjustment a : adjustments) {
            String period = periodByRun.get(a.getRunId());
            LocalDate effective = monthStart(period);
            if (effective == null || coveredMonths.contains(YearMonth.from(effective))) {
                continue;
            }
            items.add(new EmployeePayHistoryItem(effective, a.getAmount(),
                    adjustTypeReason(a), null, a.getCreatedBy(), a.getCreatedAt(), SOURCE_ADJUSTMENT));
        }
        return items;
    }

    /** 版本生效日 → 同月调整单理由（有则作为调薪原因展示）。 */
    private Map<Long, String> adjustmentReasons(List<PayrollAdjustment> adjustments,
                                                Map<Long, String> periodByRun) {
        Map<Long, String> map = new HashMap<>();
        for (PayrollAdjustment a : adjustments) {
            LocalDate effective = monthStart(periodByRun.get(a.getRunId()));
            String reason = a.getReason();
            if (effective != null && reason != null && !reason.isBlank()) {
                map.putIfAbsent(effective.toEpochDay(), reason);
            }
        }
        return map;
    }

    /** 调整单所属批次的 runId → periodCode（一次批量查询）。 */
    private Map<Long, String> runPeriods(List<PayrollAdjustment> adjustments) {
        Set<Long> runIds = new HashSet<>();
        for (PayrollAdjustment a : adjustments) {
            if (a.getRunId() != null) {
                runIds.add(a.getRunId());
            }
        }
        Map<Long, String> map = new HashMap<>();
        if (runIds.isEmpty()) {
            return map;
        }
        for (PayrollRun run : runMapper.selectBatchIds(runIds)) {
            map.put(run.getId(), run.getPeriodCode());
        }
        return map;
    }

    private String adjustTypeReason(PayrollAdjustment a) {
        String type = a.getAdjustType() == null ? "ADJUST" : a.getAdjustType();
        String reason = a.getReason();
        return reason == null || reason.isBlank() ? type : type + ": " + reason;
    }

    private LocalDate monthStart(String periodCode) {
        if (periodCode == null || periodCode.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(periodCode.trim()).atDay(1);
        } catch (DateTimeParseException e) {
            log.warn("批次 periodCode 非法，调整单轨迹跳过: {}", periodCode);
            return null;
        }
    }

    private BigDecimal parse(String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(plain.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void requireSalaryPerm() {
        Set<String> perms = AuthContextHolder.getPerms();
        if (perms == null || !perms.contains(PERM_SALARY_READ)) {
            throw new BizException(ErrorCode.CMP_SALARY_FORBIDDEN);
        }
    }
}

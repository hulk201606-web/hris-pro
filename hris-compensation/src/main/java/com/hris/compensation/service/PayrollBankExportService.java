package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.compensation.client.EmployeeClient;
import com.hris.compensation.entity.EmployeePay;
import com.hris.compensation.entity.PayrollDetail;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.EmployeePayMapper;
import com.hris.compensation.mapper.PayrollDetailMapper;
import com.hris.compensation.mapper.PayrollRunMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报盘导出（CMP-F-014 发薪指令的银行代发文件落地，CMP 报盘）。
 *
 * <p>列：员工工号/姓名/银行卡号(解密后)/实发金额/备注。银行卡号取该期间现行有效的
 * cmp_employee_pay.bank_account_cipher（TypeHandler 读时解密，业务层即明文）；工号与姓名
 * 经 emp /internal/emp/{id}/profile-brief 取，员工域不可用时降级为 empId 占位（不阻断导出）。</p>
 *
 * <p>状态守卫：仅 APPROVE/PAID/LOCK 可导出——三者均已过复核关，避免导出未审金额；
 * 空明细拒绝（PARAM_INVALID），防止生成只有表头的空报盘。</p>
 *
 * <p>备注列固定提示「银行代发模板为通用格式，各行差异需按银行适配」：本文件为通用列结构，
 * 实际提交各行（工行/建行/招行等）时需按其模板增删列、调整编码与表头，避免误当作可直接上送文件。</p>
 */
@Service
public class PayrollBankExportService {

    private static final Logger log = LoggerFactory.getLogger(PayrollBankExportService.class);

    /** 可导出的终态集合（APPROVE 起，含 PAID/LOCK）。 */
    private static final Set<String> EXPORTABLE_STATUS = Set.of("APPROVE", "PAID", "LOCK");

    /** 通用报盘备注。 */
    static final String BANK_TEMPLATE_NOTE = "银行代发模板为通用格式，各行差异需按银行适配";

    /** 表头（顺序即列契约，前端/银行适配以此为准）。 */
    static final String HEADER = "员工工号,姓名,银行卡号,实发金额,备注";

    /** UTF-8 BOM：Excel 直接打开不乱码。 */
    static final String BOM = "\uFEFF";

    private final PayrollRunMapper runMapper;
    private final PayrollDetailMapper detailMapper;
    private final EmployeePayMapper employeePayMapper;
    private final EmployeeClient employeeClient;

    public PayrollBankExportService(PayrollRunMapper runMapper, PayrollDetailMapper detailMapper,
                                    EmployeePayMapper employeePayMapper, EmployeeClient employeeClient) {
        this.runMapper = runMapper;
        this.detailMapper = detailMapper;
        this.employeePayMapper = employeePayMapper;
        this.employeeClient = employeeClient;
    }

    /** 导出文件（filename + 完整 CSV 文本，含 BOM）。 */
    public record BankExportFile(String filename, String content) {
    }

    /**
     * 生成银行代发 CSV。
     *
     * @param runId 批次主键
     */
    public BankExportFile export(Long runId) {
        PayrollRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
        if (!EXPORTABLE_STATUS.contains(run.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "当前状态不可导出报盘（" + run.getStatus() + "），仅 APPROVE/PAID/LOCK 可导出");
        }
        List<PayrollDetail> details = detailMapper.selectList(new LambdaQueryWrapper<PayrollDetail>()
                .eq(PayrollDetail::getRunId, runId)
                .orderByAsc(PayrollDetail::getEmpId));
        if (details.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "算薪明细为空，无法导出报盘");
        }
        Map<Long, String> bankAccounts = loadBankAccounts(run);
        StringBuilder csv = new StringBuilder(BOM).append(HEADER).append("\r\n");
        for (PayrollDetail d : details) {
            EmployeeInfo emp = fetchEmployee(d.getEmpId());
            csv.append(csvCell(emp.empNo())).append(',')
                    .append(csvCell(emp.name())).append(',')
                    .append(csvCell(bankAccounts.getOrDefault(d.getEmpId(), ""))).append(',')
                    .append(csvCell(plainAmount(d.getNetAmountCipher()))).append(',')
                    .append(csvCell(BANK_TEMPLATE_NOTE)).append("\r\n");
        }
        return new BankExportFile("payroll_" + run.getPeriodCode() + ".csv", csv.toString());
    }

    /** 该批次期间现行有效的定薪记录 → empId:银行卡号（明文）。 */
    private Map<Long, String> loadBankAccounts(PayrollRun run) {
        Map<Long, String> map = new LinkedHashMap<>();
        if (run.getPayGroupId() == null || run.getPeriodCode() == null) {
            return map;
        }
        YearMonth ym = YearMonth.parse(run.getPeriodCode());
        LocalDate periodStart = ym.atDay(1);
        LocalDate periodEnd = ym.atEndOfMonth();
        List<EmployeePay> pays = employeePayMapper.selectList(new LambdaQueryWrapper<EmployeePay>()
                .eq(EmployeePay::getPayGroupId, run.getPayGroupId())
                .le(EmployeePay::getEffectiveFrom, periodEnd)
                .and(w -> w.isNull(EmployeePay::getEffectiveTo)
                        .or().ge(EmployeePay::getEffectiveTo, periodStart))
                .orderByDesc(EmployeePay::getEffectiveFrom));
        for (EmployeePay p : pays) {
            if (p.getBankAccountCipher() != null && !p.getBankAccountCipher().isBlank()) {
                map.putIfAbsent(p.getEmpId(), p.getBankAccountCipher());
            }
        }
        return map;
    }

    /** 员工工号/姓名：emp 不可用降级 empId 占位，不影响导出主流程。 */
    private EmployeeInfo fetchEmployee(Long empId) {
        try {
            R<Map<String, Object>> resp = employeeClient.profileBrief(empId);
            Map<String, Object> data = resp == null ? null : resp.getData();
            if (data != null) {
                String empNo = data.get("empNo") == null ? String.valueOf(empId)
                        : String.valueOf(data.get("empNo"));
                String name = data.get("name") == null ? "" : String.valueOf(data.get("name"));
                return new EmployeeInfo(empNo, name);
            }
        } catch (Exception e) {
            log.warn("员工简报不可用，报盘降级以 empId 占位: empId={}, err={}", empId, e.getMessage());
        }
        return new EmployeeInfo(String.valueOf(empId), "");
    }

    private String plainAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return "0";
        }
        try {
            return new BigDecimal(amount.trim()).toPlainString();
        } catch (NumberFormatException e) {
            return amount.trim();
        }
    }

    /** CSV 单元转义：含逗号/引号/换行时用双引号包裹，内部引号翻倍。 */
    private String csvCell(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private record EmployeeInfo(String empNo, String name) {
    }
}

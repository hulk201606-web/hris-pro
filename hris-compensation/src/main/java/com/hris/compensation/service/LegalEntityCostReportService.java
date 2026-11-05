package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.compensation.client.EmployeeClient;
import com.hris.compensation.client.OrgClient;
import com.hris.compensation.dto.ReportDtos.EmploymentBriefRow;
import com.hris.compensation.dto.ReportDtos.HeadcountStructureBriefRequest;
import com.hris.compensation.dto.ReportDtos.LegalEntityBriefItem;
import com.hris.compensation.dto.ReportDtos.LegalEntityBriefRequest;
import com.hris.compensation.dto.ReportDtos.LegalEntityCostRow;
import com.hris.compensation.entity.PayrollDetail;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.PayrollDetailMapper;
import com.hris.compensation.mapper.PayrollRunMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CMP-F-017 多法人成本合并报表（MVP 口径：单库聚合）。
 *
 * <p><b>架构局限（docs/19 §8.4）</b>：多法人在同一库内是行级区分（tenant_id + legal_entity_id），
 * 本服务按行聚合实现；大数据量性能与跨库/跨实例合并需只读聚合库，属架构级方案，另行评审。</p>
 *
 * <p><b>法人归属口径</b>：MVP 用员工现行雇佣关系（emp_employment status=1 且 effective_to IS NULL）
 * 映射法人，不做历史雇佣回溯——期间内调动法人的员工全部计入当前法人，注明局限。</p>
 *
 * <p><b>金额口径</b>：cmp_payroll_detail.amount_cipher/net_amount_cipher 当前为 MVP 明文占位
 * （与 emp 域 cert_no_cipher 同策略，加密插件接入前按原值解析），按 DECIMAL 聚合；
 * deductionTotal = 应发 - 实发（社保+个税+其他扣减合计，无法拆分），taxTotal 恒为 null。
 * 密域关系：本端点挂 payroll read 权限码，只返回法人级汇总总额，不返回单人明细与密文，
 * 与 cmp:salary:read 密域权限（单人明文）互不影响。</p>
 */
@Service
public class LegalEntityCostReportService {

    private static final Logger log = LoggerFactory.getLogger(LegalEntityCostReportService.class);

    private final PayrollRunMapper runMapper;
    private final PayrollDetailMapper detailMapper;
    private final EmployeeClient employeeClient;
    private final OrgClient orgClient;

    public LegalEntityCostReportService(PayrollRunMapper runMapper, PayrollDetailMapper detailMapper,
                                        EmployeeClient employeeClient, OrgClient orgClient) {
        this.runMapper = runMapper;
        this.detailMapper = detailMapper;
        this.employeeClient = employeeClient;
        this.orgClient = orgClient;
    }

    /**
     * 按算薪周期聚合各法人成本总额。无批次或无明细返回空列表；org 不可用时名称降级为 null。
     */
    public List<LegalEntityCostRow> legalEntityCost(String periodCode) {
        if (periodCode == null || periodCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "periodCode 不能为空");
        }
        List<PayrollRun> runs = runMapper.selectList(new LambdaQueryWrapper<PayrollRun>()
                .eq(PayrollRun::getPeriodCode, periodCode));
        if (runs.isEmpty()) {
            return List.of();
        }
        List<PayrollDetail> details = detailMapper.selectList(new LambdaQueryWrapper<PayrollDetail>()
                .in(PayrollDetail::getRunId, runs.stream().map(PayrollRun::getId).toList()));
        if (details.isEmpty()) {
            return List.of();
        }

        // empId -> 法人（现行雇佣关系；emp 简报不可用时降级为空映射，全部归入法人未知的聚合行）
        Map<Long, Long> empEntity = employmentEntityMap(
                details.stream().map(PayrollDetail::getEmpId).distinct().toList());

        Map<Long, Agg> byEntity = new HashMap<>();
        for (PayrollDetail d : details) {
            Agg agg = byEntity.computeIfAbsent(empEntity.get(d.getEmpId()), k -> new Agg());
            agg.empIds.add(d.getEmpId());
            BigDecimal gross = parseAmount(d.getAmountCipher());
            BigDecimal net = parseAmount(d.getNetAmountCipher());
            if (gross != null) {
                agg.gross = agg.gross.add(gross);
            }
            if (net != null) {
                agg.net = agg.net.add(net);
            }
        }

        Map<Long, String> names = legalEntityNames(byEntity.keySet());
        List<LegalEntityCostRow> rows = new ArrayList<>();
        for (Map.Entry<Long, Agg> e : byEntity.entrySet()) {
            Agg a = e.getValue();
            rows.add(new LegalEntityCostRow(e.getKey(),
                    e.getKey() == null ? null : names.get(e.getKey()), a.empIds.size(),
                    a.gross, a.gross.subtract(a.net), null, a.net));
        }
        rows.sort((x, y) -> Long.compare(
                x.legalEntityId() == null ? Long.MIN_VALUE : x.legalEntityId(),
                y.legalEntityId() == null ? Long.MIN_VALUE : y.legalEntityId()));
        return rows;
    }

    /** 批量取员工现行法人映射；emp 简报不可用降级为空 Map（fail-open，报表仍返回法人未知行）。 */
    private Map<Long, Long> employmentEntityMap(List<Long> empIds) {
        try {
            R<List<EmploymentBriefRow>> resp = employeeClient.headcountStructureBrief(
                    new HeadcountStructureBriefRequest(null));
            List<EmploymentBriefRow> rows = resp == null ? null : resp.getData();
            Map<Long, Long> map = new HashMap<>();
            if (rows != null) {
                Set<Long> wanted = new HashSet<>(empIds);
                for (EmploymentBriefRow r : rows) {
                    if (r.empId() != null && wanted.contains(r.empId())) {
                        map.put(r.empId(), r.legalEntityId());
                    }
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("emp 雇佣简报不可用，法人归属降级为未知: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 法人名称富化；org 不可用降级为空 Map（name=null，fail-open）。 */
    private Map<Long, String> legalEntityNames(Set<Long> entityIds) {
        List<Long> ids = entityIds.stream().filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            R<List<LegalEntityBriefItem>> resp = orgClient.legalEntityBrief(new LegalEntityBriefRequest(ids));
            List<LegalEntityBriefItem> items = resp == null ? null : resp.getData();
            Map<Long, String> map = new HashMap<>();
            if (items != null) {
                for (LegalEntityBriefItem i : items) {
                    if (i.id() != null) {
                        map.put(i.id(), i.name());
                    }
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("org 法人简报不可用，法人名称降级为 null: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 密文列按金额解析（MVP 明文占位）；非数值/空值返回 null 跳过，不阻断聚合。 */
    private BigDecimal parseAmount(String cipher) {
        if (cipher == null || cipher.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(cipher.trim());
        } catch (NumberFormatException e) {
            log.warn("算薪明细金额列非数值，聚合跳过该值");
            return null;
        }
    }

    /** 聚合累加器：empIds 用于去重人数，gross/net 为 DECIMAL 累加。 */
    private static final class Agg {
        private final Set<Long> empIds = new HashSet<>();
        private BigDecimal gross = BigDecimal.ZERO;
        private BigDecimal net = BigDecimal.ZERO;
    }
}

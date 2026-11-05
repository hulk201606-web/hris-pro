package com.hris.compensation.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.compensation.client.EmployeeClient;
import com.hris.compensation.client.OrgClient;
import com.hris.compensation.dto.ReportDtos.EmploymentBriefRow;
import com.hris.compensation.dto.ReportDtos.HeadcountStructureBriefRequest;
import com.hris.compensation.dto.ReportDtos.LegalEntityBriefItem;
import com.hris.compensation.dto.ReportDtos.LegalEntityBriefRequest;
import com.hris.compensation.dto.ReportDtos.LegalEntityCostRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CMP-F-017 多法人成本合并报表（MVP 口径：聚合查询走可选只读聚合数据源）。
 *
 * <p><b>聚合数据源（批 8 #17 MVP）</b>：run/明细两条聚合查询经 aggregateJdbcTemplate 执行，
 * 配置 spring.datasource.aggregate.url 时路由到聚合只读库，缺省回退主库（配置驱动切换，
 * 见 AggregateDataSourceConfig）。真·独立聚合库由 DBA 供数后仅改 URL。SQL 显式携带
 * tenant_id（脱离 MP TenantLine 插件链路，隔离语义不变）+ deleted=0 逻辑删除过滤。</p>
 *
 * <p><b>法人归属口径</b>：MVP 用员工现行雇佣关系（emp_employment status=1 且 effective_to IS NULL）
 * 映射法人，不做历史雇佣回溯——期间内调动法人的员工全部计入当前法人，注明局限。</p>
 *
 * <p><b>金额口径</b>：cmp_payroll_detail.amount_cipher/net_amount_cipher 当前为 MVP 明文占位
 * （与 emp 域 cert_no_cipher 同策略，加密插件接入前按原值解析），按 DECIMAL 聚合；
 * deductionTotal = 应发 - 实发（社保+个税+其他扣减合计，无法拆分），taxTotal 恒为 null。
 * 密域关系：本端点挂 payroll read 权限码，只返回法人级汇总总额，不返回单人明细与密文。</p>
 */
@Service
public class LegalEntityCostReportService {

    private static final Logger log = LoggerFactory.getLogger(LegalEntityCostReportService.class);

    private static final String RUN_IDS_SQL = """
            SELECT id FROM cmp_payroll_run
            WHERE period_code = ? AND tenant_id = ? AND deleted = 0
            """;

    private static final String DETAIL_SQL_PREFIX = """
            SELECT run_id, emp_id, amount_cipher, net_amount_cipher FROM cmp_payroll_detail
            WHERE run_id IN (""";

    private static final String DETAIL_SQL_SUFFIX = ") AND tenant_id = ? AND deleted = 0";

    private final JdbcTemplate jdbcTemplate;
    private final EmployeeClient employeeClient;
    private final OrgClient orgClient;
    /** 快照读取能力（批 12 #29）：可选装配，source=snapshot 时使用。 */
    private CostSnapshotService costSnapshotService;

    public LegalEntityCostReportService(
            @Qualifier("aggregateJdbcTemplate") JdbcTemplate jdbcTemplate,
            EmployeeClient employeeClient, OrgClient orgClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.employeeClient = employeeClient;
        this.orgClient = orgClient;
    }

    /** 可选依赖注入（保留原构造签名，避免影响既有装配与单测）。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setCostSnapshotService(CostSnapshotService service) {
        this.costSnapshotService = service;
    }

    /**
     * 按来源取报表：{@code live}（缺省）/ {@code snapshot}（读物化快照）。
     * 快照不存法人名称（历史快照不与 org 改名联动），name 恒为 null。
     *
     * @throws BizException source 非法
     */
    public List<LegalEntityCostRow> legalEntityCost(String periodCode, String source) {
        String mode = source == null || source.isBlank() ? "live" : source.trim().toLowerCase();
        if ("live".equals(mode)) {
            return legalEntityCost(periodCode);
        }
        if ("snapshot".equals(mode)) {
            if (costSnapshotService == null) {
                throw new BizException(ErrorCode.CMP_ERROR, "快照能力未装配，请使用 source=live");
            }
            return costSnapshotService.reportRows(periodCode);
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "source 仅支持 live|snapshot");
    }

    /** 明细聚合行（脱离 MP 实体链路的轻量读取模型，包内可见供单测构造）。 */
    record DetailRow(long runId, long empId, String amountCipher, String netAmountCipher) {
    }

    /**
     * 按算薪周期聚合各法人成本总额。无批次或无明细返回空列表；org 不可用时名称降级为 null。
     */
    public List<LegalEntityCostRow> legalEntityCost(String periodCode) {
        if (periodCode == null || periodCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "periodCode 不能为空");
        }
        Long tenantId = TraceContext.getTenantId();
        List<Long> runIds = jdbcTemplate.query(RUN_IDS_SQL,
                (rs, i) -> rs.getLong("id"), periodCode, tenantId);
        if (runIds.isEmpty()) {
            return List.of();
        }
        List<DetailRow> details = queryDetails(runIds, tenantId);
        if (details.isEmpty()) {
            return List.of();
        }

        // empId -> 法人（现行雇佣关系；emp 简报不可用时降级为空映射，全部归入法人未知的聚合行）
        Map<Long, Long> empEntity = employmentEntityMap(
                details.stream().map(DetailRow::empId).distinct().toList());

        Map<Long, Agg> byEntity = new HashMap<>();
        for (DetailRow d : details) {
            Agg agg = byEntity.computeIfAbsent(empEntity.get(d.empId()), k -> new Agg());
            agg.empIds.add(d.empId());
            BigDecimal gross = parseAmount(d.amountCipher());
            BigDecimal net = parseAmount(d.netAmountCipher());
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

    /** 明细批量读取：run_id IN 动态占位 + 显式租户/逻辑删除过滤（聚合查询走聚合数据源）。 */
    private List<DetailRow> queryDetails(List<Long> runIds, Long tenantId) {
        String placeholders = String.join(",", java.util.Collections.nCopies(runIds.size(), "?"));
        List<Object> args = new ArrayList<>(runIds);
        args.add(tenantId);
        return jdbcTemplate.query(DETAIL_SQL_PREFIX + placeholders + DETAIL_SQL_SUFFIX,
                (rs, i) -> new DetailRow(rs.getLong("run_id"), rs.getLong("emp_id"),
                        rs.getString("amount_cipher"), rs.getString("net_amount_cipher")),
                args.toArray());
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

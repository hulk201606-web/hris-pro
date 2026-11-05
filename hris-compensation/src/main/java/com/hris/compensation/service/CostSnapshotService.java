package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.compensation.dto.CostSnapshotDtos.CostSnapshotRow;
import com.hris.compensation.dto.CostSnapshotDtos.RefreshResponse;
import com.hris.compensation.dto.ReportDtos.LegalEntityCostRow;
import com.hris.compensation.entity.CostSnapshot;
import com.hris.compensation.mapper.CostSnapshotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 法人成本物化快照（批 12 #29）：把「算薪明细 → 法人汇总」的重查询结果物化到
 * {@code cmp_cost_snapshot}，报表可用 {@code source=snapshot} 走聚合只读库直读。
 *
 * <p><b>写读分离</b>：刷新（重算 + upsert）走主库（MP Mapper，事务内）；
 * 查询走 {@code aggregateJdbcTemplate}（配置 spring.datasource.aggregate.url 时路由到
 * 聚合只读库，缺省回退主库）。真·独立聚合库场景由 DBA 把该表同步/复制到聚合库，
 * 刷新仍落主库（避免只读库写入）。</p>
 *
 * <p><b>调度</b>：不在本模块内置 @Scheduled——由运营在 platform 调度中心注册<b>月度</b>任务：
 * {@code POST /api/v1/cmp/reports/cost-snapshot/refresh?periodCode=YYYY-MM}
 * （建议每月 1 日 02:00，紧接上月算薪关账后执行；租户上下文由调度中心透传 X-Tenant-Id）。</p>
 *
 * <p><b>口径</b>：与 CMP-F-017 一致（法人归属取现行雇佣关系，不做历史回溯；
 * taxTotal 无逐人个税数据源恒为 null；deductionTotal = 应发 - 实发）。
 * 法人未知的聚合行以 {@code legal_entity_id=0} 落库（保证唯一键生效），出参还原为 null。</p>
 */
@Service
public class CostSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(CostSnapshotService.class);

    private static final String QUERY_SQL = """
            SELECT legal_entity_id, headcount, gross_total, deduction_total, tax_total,
                   net_total, refreshed_at
            FROM cmp_cost_snapshot
            WHERE period_code = ? AND tenant_id = ? AND deleted = 0
            ORDER BY legal_entity_id
            """;

    private final CostSnapshotMapper mapper;
    private final JdbcTemplate aggregateJdbcTemplate;

    public CostSnapshotService(CostSnapshotMapper mapper,
                               @Qualifier("aggregateJdbcTemplate") JdbcTemplate aggregateJdbcTemplate) {
        this.mapper = mapper;
        this.aggregateJdbcTemplate = aggregateJdbcTemplate;
    }

    /**
     * 重算并物化该期快照（事务）：逐法人 upsert，随后清理本期已不存在的法人行。
     *
     * @param periodCode 周期编码（如 2026-01）
     * @param rows       实时聚合结果（由 {@link LegalEntityCostReportService#legalEntityCost} 产出）
     */
    @Transactional(rollbackFor = Exception.class)
    public RefreshResponse refresh(String periodCode, List<LegalEntityCostRow> rows) {
        requirePeriod(periodCode);
        Long tenantId = TraceContext.getTenantId();
        LocalDateTime now = LocalDateTime.now();
        Set<Long> entityIds = new HashSet<>();
        int affected = 0;
        List<LegalEntityCostRow> source = rows == null ? List.of() : rows;
        for (LegalEntityCostRow row : source) {
            long entityId = row.legalEntityId() == null ? CostSnapshot.UNKNOWN_ENTITY : row.legalEntityId();
            entityIds.add(entityId);
            CostSnapshot s = new CostSnapshot();
            s.setId(IdWorker.getId());
            s.setTenantId(tenantId);
            s.setPeriodCode(periodCode);
            s.setLegalEntityId(entityId);
            s.setHeadcount((int) row.headcount());
            s.setGrossTotal(nz(row.grossTotal()));
            s.setDeductionTotal(nz(row.deductionTotal()));
            s.setTaxTotal(row.taxTotal());
            s.setNetTotal(nz(row.netTotal()));
            s.setRefreshedAt(now);
            s.setCreatedBy(0L);
            s.setUpdatedBy(0L);
            s.setCreatedAt(now);
            s.setUpdatedAt(now);
            affected += mapper.upsert(s);
        }
        int removed = mapper.deleteStale(tenantId, periodCode, entityIds.isEmpty() ? null : entityIds);
        log.info("成本快照刷新完成 period={} tenant={} rows={} affected={} removed={}",
                periodCode, tenantId, source.size(), affected, removed);
        return new RefreshResponse(periodCode, source.size(), affected, now);
    }

    /** 读快照（聚合数据源）：无数据返回空列表。 */
    public List<CostSnapshotRow> list(String periodCode) {
        requirePeriod(periodCode);
        Long tenantId = TraceContext.getTenantId();
        return aggregateJdbcTemplate.query(QUERY_SQL, (rs, i) -> {
            long entityId = rs.getLong("legal_entity_id");
            return new CostSnapshotRow(entityId == CostSnapshot.UNKNOWN_ENTITY ? null : entityId,
                    rs.getInt("headcount"), rs.getBigDecimal("gross_total"),
                    rs.getBigDecimal("deduction_total"), rs.getBigDecimal("tax_total"),
                    rs.getBigDecimal("net_total"),
                    rs.getTimestamp("refreshed_at") == null ? null
                            : rs.getTimestamp("refreshed_at").toLocalDateTime());
        }, periodCode, tenantId);
    }

    /**
     * 报表视图（source=snapshot）：与 live 报表同结构，法人名称为 null
     * （快照不存名称，避免 org 改名导致历史快照不一致；需要名称时由前端按 ID 富化）。
     */
    public List<LegalEntityCostRow> reportRows(String periodCode) {
        return list(periodCode).stream()
                .map(r -> new LegalEntityCostRow(r.legalEntityId(), null, r.headcount(),
                        r.grossTotal(), r.deductionTotal(), r.taxTotal(), r.netTotal()))
                .toList();
    }

    private static void requirePeriod(String periodCode) {
        if (periodCode == null || periodCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "periodCode 不能为空");
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

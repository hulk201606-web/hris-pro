package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.compensation.dto.PayrollCompareDtos.EmployeeCompare;
import com.hris.compensation.dto.PayrollCompareDtos.ItemDiff;
import com.hris.compensation.dto.PayrollCompareDtos.PayrollCompareResponse;
import com.hris.compensation.entity.PayrollDetail;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.PayrollDetailMapper;
import com.hris.compensation.mapper.PayrollRunMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 试算环比差异对比（CMP-F-013a）。
 *
 * <p>口径：baseRunId 缺省取同 periodCode 的上一个批次（按 created_at 倒序第一条，
 * 严格早于当前批次）；无上期时返回空员工列表与说明文案，不报错（试算首次执行即此场景）。</p>
 *
 * <p>金额来源：cmp_payroll_detail.amount_cipher/net_amount_cipher 经 CipherTypeHandler
 * 读取即明文（业务层聚合口径）；逐项金额取 input_snapshot.items（算薪快照，可复算）。
 * 员工并集口径：仅上期有的员工 current 记 0，仅本期有的员工 base 记 0，避免新增/离职员工
 * 被静默丢弃（差异对比要能看见人员变动）。</p>
 */
@Service
public class PayrollCompareService {

    private static final Logger log = LoggerFactory.getLogger(PayrollCompareService.class);

    /** 仅用于读取快照 JSON（线程安全）。 */
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String NOTE_NO_BASE = "同期间无上期批次，无可对比数据";

    private final PayrollRunMapper runMapper;
    private final PayrollDetailMapper detailMapper;

    public PayrollCompareService(PayrollRunMapper runMapper, PayrollDetailMapper detailMapper) {
        this.runMapper = runMapper;
        this.detailMapper = detailMapper;
    }

    /**
     * 两批次逐员工明细对比。
     *
     * @param runId     当前批次
     * @param baseRunId 基期批次，null=自动取同期间上一个批次
     */
    public PayrollCompareResponse compare(Long runId, Long baseRunId) {
        PayrollRun current = requireRun(runId);
        PayrollRun base = baseRunId != null ? requireRun(baseRunId) : previousRun(current);
        if (base == null) {
            return new PayrollCompareResponse(runId, null, current.getPeriodCode(), null, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, null, List.of(), NOTE_NO_BASE);
        }
        Map<Long, DetailSnapshot> currentSnapshots = loadSnapshots(runId);
        Map<Long, DetailSnapshot> baseSnapshots = loadSnapshots(base.getId());

        Set<Long> empIds = new LinkedHashSet<>(currentSnapshots.keySet());
        empIds.addAll(baseSnapshots.keySet());

        List<EmployeeCompare> employees = new ArrayList<>();
        BigDecimal grossTotal = BigDecimal.ZERO;
        BigDecimal netTotal = BigDecimal.ZERO;
        BigDecimal baseNetTotal = BigDecimal.ZERO;
        for (Long empId : empIds.stream().sorted().toList()) {
            DetailSnapshot cur = currentSnapshots.getOrDefault(empId, DetailSnapshot.EMPTY);
            DetailSnapshot old = baseSnapshots.getOrDefault(empId, DetailSnapshot.EMPTY);
            EmployeeCompare row = diffEmployee(empId, old, cur);
            employees.add(row);
            grossTotal = grossTotal.add(row.grossDiff());
            netTotal = netTotal.add(row.netDiff());
            baseNetTotal = baseNetTotal.add(old.net());
        }
        return new PayrollCompareResponse(runId, base.getId(), current.getPeriodCode(),
                base.getPeriodCode(), employees.size(), grossTotal, netTotal,
                rate(netTotal, baseNetTotal), employees, null);
    }

    private EmployeeCompare diffEmployee(Long empId, DetailSnapshot base, DetailSnapshot current) {
        Set<String> codes = new LinkedHashSet<>(current.items().keySet());
        codes.addAll(base.items().keySet());
        List<ItemDiff> items = new ArrayList<>();
        for (String code : codes) {
            BigDecimal b = base.items().getOrDefault(code, BigDecimal.ZERO);
            BigDecimal c = current.items().getOrDefault(code, BigDecimal.ZERO);
            items.add(new ItemDiff(code, b, c, c.subtract(b)));
        }
        BigDecimal grossDiff = current.gross().subtract(base.gross());
        BigDecimal netDiff = current.net().subtract(base.net());
        return new EmployeeCompare(empId, items, grossDiff, netDiff, rate(netDiff, base.net()));
    }

    /** 环比 = 差额/基期，基期 0 时无意义返回 null（前端显示「-」）。 */
    private BigDecimal rate(BigDecimal diff, BigDecimal baseAmount) {
        if (baseAmount == null || baseAmount.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return diff.divide(baseAmount, 4, RoundingMode.HALF_UP);
    }

    /** 同期间上一个批次：created_at 严格早于当前，倒序取第一条。 */
    private PayrollRun previousRun(PayrollRun current) {
        if (current.getCreatedAt() == null) {
            return null;
        }
        return runMapper.selectOne(new LambdaQueryWrapper<PayrollRun>()
                .eq(PayrollRun::getPeriodCode, current.getPeriodCode())
                .lt(PayrollRun::getCreatedAt, current.getCreatedAt())
                .orderByDesc(PayrollRun::getCreatedAt)
                .last("limit 1"));
    }

    private Map<Long, DetailSnapshot> loadSnapshots(Long runId) {
        List<PayrollDetail> details = detailMapper.selectList(new LambdaQueryWrapper<PayrollDetail>()
                .eq(PayrollDetail::getRunId, runId)
                .orderByAsc(PayrollDetail::getEmpId));
        Map<Long, DetailSnapshot> map = new LinkedHashMap<>();
        for (PayrollDetail d : details) {
            map.put(d.getEmpId(), new DetailSnapshot(
                    parse(d.getAmountCipher()), parse(d.getNetAmountCipher()), parseItems(d.getInputSnapshot())));
        }
        return map;
    }

    /** 快照 items 为 {code: amount} 结构；解析失败按空明细处理（不阻断对比）。 */
    private Map<String, BigDecimal> parseItems(String snapshot) {
        Map<String, BigDecimal> items = new LinkedHashMap<>();
        if (snapshot == null || snapshot.isBlank()) {
            return items;
        }
        try {
            JsonNode node = JSON.readTree(snapshot).path("items");
            node.fields().forEachRemaining(e -> {
                BigDecimal v = toDecimal(e.getValue());
                if (v != null) {
                    items.put(e.getKey(), v);
                }
            });
        } catch (Exception e) {
            log.warn("算薪快照解析失败，逐项差异按空处理: {}", e.getMessage());
        }
        return items;
    }

    private BigDecimal toDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parse(String plain) {
        if (plain == null || plain.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(plain.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private PayrollRun requireRun(Long id) {
        PayrollRun run = runMapper.selectById(id);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
        return run;
    }

    /** 单员工明细快照（应发/实发/逐项）。 */
    private record DetailSnapshot(BigDecimal gross, BigDecimal net, Map<String, BigDecimal> items) {
        static final DetailSnapshot EMPTY =
                new DetailSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, Map.of());
    }
}

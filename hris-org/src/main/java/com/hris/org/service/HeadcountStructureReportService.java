package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.client.EmployeeClient;
import com.hris.org.dto.ReportDtos.DeptCount;
import com.hris.org.dto.ReportDtos.HeadcountStructureBriefRequest;
import com.hris.org.dto.ReportDtos.HeadcountStructureBriefRow;
import com.hris.org.dto.ReportDtos.HeadcountStructureResponse;
import com.hris.org.dto.ReportDtos.HireTypeCount;
import com.hris.org.dto.ReportDtos.StatusCount;
import com.hris.org.entity.OrgDept;
import com.hris.org.mapper.OrgDeptMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * P1 人力结构分析报表（纯聚合，读 emp 简报 + org 部门主数据）。
 *
 * <p>数据源：POST /internal/emp/headcount-structure-brief（员工×雇佣关系批量简报），
 * org 侧只做维度聚合与部门名称富化。emp 不可用时 fail-open 降级为空数据（报表可用性优先）。</p>
 *
 * <p>部门维度口径：deptScope=top 时按物化路径（org_dept.path，如 1.12.130）将每个部门
 * 人数滚动聚合到一级部门（path 首段）；deptScope=all 时按员工直属部门逐部门计数。</p>
 */
@Service
public class HeadcountStructureReportService {

    private static final Logger log = LoggerFactory.getLogger(HeadcountStructureReportService.class);

    /** 部门聚合口径：一级部门滚动聚合。 */
    public static final String SCOPE_TOP = "top";
    /** 部门聚合口径：逐部门（员工直属部门）计数。 */
    public static final String SCOPE_ALL = "all";

    private final EmployeeClient employeeClient;
    private final OrgDeptMapper deptMapper;

    public HeadcountStructureReportService(EmployeeClient employeeClient, OrgDeptMapper deptMapper) {
        this.employeeClient = employeeClient;
        this.deptMapper = deptMapper;
    }

    /**
     * 人力结构分析：{byDept, byHireType, byStatus} 三维度人数聚合。
     *
     * @param deptScope top=一级部门滚动聚合（默认）；all=逐部门计数
     */
    public HeadcountStructureResponse structure(String deptScope) {
        String scope = deptScope == null || deptScope.isBlank() ? SCOPE_TOP : deptScope;
        if (!SCOPE_TOP.equals(scope) && !SCOPE_ALL.equals(scope)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "deptScope 仅支持 top/all");
        }
        List<HeadcountStructureBriefRow> rows = fetchBrief();
        if (rows.isEmpty()) {
            return new HeadcountStructureResponse(List.of(), List.of(), List.of());
        }

        // byHireType / byStatus：直接分组计数
        Map<String, Long> byHireType = rows.stream()
                .filter(r -> r.hireType() != null && !r.hireType().isBlank())
                .collect(Collectors.groupingBy(HeadcountStructureBriefRow::hireType, Collectors.counting()));
        Map<Integer, Long> byStatus = rows.stream()
                .filter(r -> r.status() != null)
                .collect(Collectors.groupingBy(HeadcountStructureBriefRow::status, Collectors.counting()));

        // byDept：部门路径映射（含名称富化），top 口径滚动到一级部门
        Map<Long, OrgDept> deptMap = deptMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(OrgDept::getId, d -> d, (a, b) -> a));
        Map<Long, Long> byDeptId = new HashMap<>();
        for (HeadcountStructureBriefRow r : rows) {
            Long deptId = r.deptId();
            if (deptId == null) {
                continue;
            }
            Long target = SCOPE_TOP.equals(scope) ? topLevelOf(deptId, deptMap) : deptId;
            byDeptId.merge(target, 1L, Long::sum);
        }
        List<DeptCount> byDept = byDeptId.entrySet().stream()
                .map(e -> new DeptCount(e.getKey(),
                        deptMap.containsKey(e.getKey()) ? deptMap.get(e.getKey()).getName() : null,
                        e.getValue()))
                .sorted((a, b) -> Long.compare(a.deptId() == null ? 0 : a.deptId(),
                        b.deptId() == null ? 0 : b.deptId()))
                .toList();

        return new HeadcountStructureResponse(byDept,
                byHireType.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> new HireTypeCount(e.getKey(), e.getValue()))
                        .toList(),
                byStatus.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> new StatusCount(e.getKey(), e.getValue()))
                        .toList());
    }

    /** 取 emp 简报（现行口径）；emp 不可用降级为空列表（fail-open）。 */
    private List<HeadcountStructureBriefRow> fetchBrief() {
        try {
            R<List<HeadcountStructureBriefRow>> resp =
                    employeeClient.headcountStructureBrief(new HeadcountStructureBriefRequest(null));
            return resp == null || resp.getData() == null ? List.of() : resp.getData();
        } catch (Exception e) {
            log.warn("emp 人力结构简报不可用，报表降级为空数据: {}", e.getMessage());
            return List.of();
        }
    }

    /** 一级部门解析：物化路径首段即一级部门 id；路径缺失/非法时回退自身。 */
    private Long topLevelOf(Long deptId, Map<Long, OrgDept> deptMap) {
        OrgDept dept = deptMap.get(deptId);
        if (dept == null || dept.getPath() == null || dept.getPath().isBlank()) {
            return deptId;
        }
        String first = dept.getPath().split("\\.")[0];
        try {
            return Long.parseLong(first);
        } catch (NumberFormatException e) {
            return deptId;
        }
    }
}

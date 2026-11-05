package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.OrgVersionDtos.OrgVersionDetail;
import com.hris.org.dto.OrgVersionDtos.OrgVersionSummary;
import com.hris.org.entity.OrgGrade;
import com.hris.org.entity.OrgGradeVersion;
import com.hris.org.entity.OrgPosition;
import com.hris.org.entity.OrgPositionDept;
import com.hris.org.entity.OrgPositionVersion;
import com.hris.org.enums.OrgVersionStatus;
import com.hris.org.mapper.OrgGradeMapper;
import com.hris.org.mapper.OrgGradeVersionMapper;
import com.hris.org.mapper.OrgPositionDeptMapper;
import com.hris.org.mapper.OrgPositionMapper;
import com.hris.org.mapper.OrgPositionVersionMapper;
import com.hris.org.util.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织版本服务（批 8 #16 MVP：组织版本扩展到岗位/职级；批 11 #23 增加草稿状态机）。
 *
 * <p>选型：与 org_dept_history 同构的新表 org_position_version / org_grade_version
 * （+version_no 批次标识），不为 org_dept_history 加 type 维度（dept_id 语义会被破坏）。</p>
 *
 * <p>批 11 #23（ORG-F-004）：版本表加 status（DRAFT/ACTIVE/SUPERSEDED）与 effective_from。
 * 创建默认 DRAFT（仅管理端可见）；到期由 {@link OrgVersionStateService#activateDue} 激活，
 * 或管理端手动 activate；回滚 = 激活历史版本（原 ACTIVE 自动 SUPERSEDED）。可见性：
 * 查询默认只返回 ACTIVE，DRAFT/ALL 需 org:dept:write（控制器侧判定）。</p>
 *
 * <p>MVP 深度控制：版本创建=全量快照岗位（含挂靠部门）与职级；回滚恢复主数据与岗位挂靠，
 * 不级联员工归属重排（员工挂岗位在 emp 域，跨服务）；版本后新增的岗位/职级不删除（局限，
 * 如实登记 docs/21）。</p>
 */
@Service
public class OrgVersionService {

    private final OrgPositionMapper positionMapper;
    private final OrgGradeMapper gradeMapper;
    private final OrgPositionDeptMapper positionDeptMapper;
    private final OrgPositionVersionMapper positionVersionMapper;
    private final OrgGradeVersionMapper gradeVersionMapper;
    private final OrgVersionStateService stateService;

    public OrgVersionService(OrgPositionMapper positionMapper, OrgGradeMapper gradeMapper,
                             OrgPositionDeptMapper positionDeptMapper,
                             OrgPositionVersionMapper positionVersionMapper,
                             OrgGradeVersionMapper gradeVersionMapper,
                             OrgVersionStateService stateService) {
        this.positionMapper = positionMapper;
        this.gradeMapper = gradeMapper;
        this.positionDeptMapper = positionDeptMapper;
        this.positionVersionMapper = positionVersionMapper;
        this.gradeVersionMapper = gradeVersionMapper;
        this.stateService = stateService;
    }

    /**
     * 创建组织版本：全量快照全部岗位（含挂靠部门）与职级，版本号 V+雪花 ID。
     * 新版本一律为 DRAFT（批 11 #23）；effectiveFrom 为空按当天。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrgVersionSummary create(LocalDate effectiveFrom) {
        String versionNo = "V" + IdWorker.getId();
        LocalDate date = LocalDate.now();
        LocalDate effective = effectiveFrom == null ? date : effectiveFrom;

        List<OrgPosition> positions = positionMapper.selectList(null);
        for (OrgPosition p : positions) {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("id", p.getId());
            snap.put("name", p.getName());
            snap.put("code", p.getCode());
            snap.put("status", p.getStatus());
            snap.put("jobFamily", p.getJobFamily());
            snap.put("deptIds", positionDeptMapper.selectList(new LambdaQueryWrapper<OrgPositionDept>()
                            .eq(OrgPositionDept::getPositionId, p.getId()))
                    .stream().map(OrgPositionDept::getDeptId).toList());
            insertVersion(versionNo, date, effective, p.getId(), snap, true);
        }

        List<OrgGrade> grades = gradeMapper.selectList(null);
        for (OrgGrade g : grades) {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("id", g.getId());
            snap.put("name", g.getName());
            snap.put("code", g.getCode());
            snap.put("gradeBandId", g.getGradeBandId());
            snap.put("status", g.getStatus());
            insertVersion(versionNo, date, effective, g.getId(), snap, false);
        }
        return new OrgVersionSummary(versionNo, date.toString(), positions.size(), grades.size(),
                OrgVersionStatus.DRAFT.name(), effective.toString());
    }

    /**
     * 版本列表（按创建先后倒序）。filter 为 null 表示不限状态（含 DRAFT，仅管理端可调用）。
     * 历史遗留行 status 为空按 ACTIVE 处理。
     */
    public List<OrgVersionSummary> list(OrgVersionStatus filter) {
        Map<String, List<OrgPositionVersion>> byVersion = new LinkedHashMap<>();
        for (OrgPositionVersion row : positionVersionMapper.selectList(null)) {
            byVersion.computeIfAbsent(row.getVersionNo(), k -> new ArrayList<>()).add(row);
        }
        Map<String, Long> gradeCounts = new LinkedHashMap<>();
        Map<String, OrgGradeVersion> gradeFirst = new LinkedHashMap<>();
        for (OrgGradeVersion row : gradeVersionMapper.selectList(null)) {
            gradeCounts.merge(row.getVersionNo(), 1L, Long::sum);
            gradeFirst.putIfAbsent(row.getVersionNo(), row);
        }
        Set<String> allVersions = new LinkedHashSet<>(byVersion.keySet());
        allVersions.addAll(gradeCounts.keySet());

        List<VersionAgg> aggs = new ArrayList<>();
        for (String versionNo : allVersions) {
            List<OrgPositionVersion> posRows = byVersion.getOrDefault(versionNo, List.of());
            OrgPositionVersion head = posRows.isEmpty() ? null : posRows.get(0);
            OrgGradeVersion gradeHead = gradeFirst.get(versionNo);
            OrgVersionStatus status = normalized(head == null ? gradeHead.getStatus() : head.getStatus());
            if (filter != null && filter != status) {
                continue;
            }
            LocalDate snapshotDate = head == null ? gradeHead.getSnapshotDate() : head.getSnapshotDate();
            LocalDate effective = head == null ? gradeHead.getEffectiveFrom() : head.getEffectiveFrom();
            long sortId = head == null ? gradeHead.getId() : head.getId();
            aggs.add(new VersionAgg(versionNo, snapshotDate.toString(), posRows.size(),
                    gradeCounts.getOrDefault(versionNo, 0L), status.name(), str(effective), sortId));
        }
        return aggs.stream()
                .sorted(Comparator.comparingLong(VersionAgg::sortId).reversed())
                .map(a -> new OrgVersionSummary(a.versionNo(), a.snapshotDate(), a.positionCount(),
                        a.gradeCount(), a.status(), a.effectiveFrom()))
                .toList();
    }

    /** 版本聚合中间结果（仅用于状态过滤与排序，不对外暴露）。 */
    private record VersionAgg(String versionNo, String snapshotDate, long positionCount,
                              long gradeCount, String status, String effectiveFrom, long sortId) {
    }

    /** 单版本摘要（激活/回滚后回传给前端；版本不存在抛 NOT_FOUND）。 */
    public OrgVersionSummary summary(String versionNo) {
        return list(null).stream()
                .filter(s -> s.versionNo().equals(versionNo))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "组织版本不存在"));
    }

    /** 版本查看：DRAFT 仅管理端可见（includeDraft=false 时按不存在处理）。 */
    public OrgVersionDetail get(String versionNo, boolean includeDraft) {
        List<OrgPositionVersion> positionRows = positionVersionMapper.selectList(
                new LambdaQueryWrapper<OrgPositionVersion>()
                        .eq(OrgPositionVersion::getVersionNo, versionNo));
        List<OrgGradeVersion> gradeRows = gradeVersionMapper.selectList(
                new LambdaQueryWrapper<OrgGradeVersion>()
                        .eq(OrgGradeVersion::getVersionNo, versionNo));
        if (positionRows.isEmpty() && gradeRows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织版本不存在");
        }
        OrgVersionStatus status = normalized(positionRows.isEmpty()
                ? gradeRows.get(0).getStatus() : positionRows.get(0).getStatus());
        if (status == OrgVersionStatus.DRAFT && !includeDraft) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织版本不存在");
        }
        LocalDate snapshotDate = positionRows.isEmpty()
                ? gradeRows.get(0).getSnapshotDate() : positionRows.get(0).getSnapshotDate();
        LocalDate effective = positionRows.isEmpty()
                ? gradeRows.get(0).getEffectiveFrom() : positionRows.get(0).getEffectiveFrom();
        List<Map<String, Object>> positions = positionRows.stream()
                .map(r -> JsonUtil.toMap(r.getSnapshot())).toList();
        List<Map<String, Object>> grades = gradeRows.stream()
                .map(r -> JsonUtil.toMap(r.getSnapshot())).toList();
        return new OrgVersionDetail(versionNo, snapshotDate.toString(), status.name(),
                str(effective), positions, grades);
    }

    /**
     * 版本回滚：激活历史版本（原 ACTIVE 置 SUPERSEDED）并按快照恢复岗位/职级主数据与
     * 岗位挂靠部门（upsert，按 ID 定位）。草稿版本不可回滚（须先激活）。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrgVersionSummary rollback(String versionNo) {
        if (stateService.statusOf(versionNo) == OrgVersionStatus.DRAFT) {
            throw new BizException(ErrorCode.PARAM_INVALID, "草稿版本不可回滚，请先激活");
        }
        List<OrgPositionVersion> positionRows = positionVersionMapper.selectList(
                new LambdaQueryWrapper<OrgPositionVersion>()
                        .eq(OrgPositionVersion::getVersionNo, versionNo));
        List<OrgGradeVersion> gradeRows = gradeVersionMapper.selectList(
                new LambdaQueryWrapper<OrgGradeVersion>()
                        .eq(OrgGradeVersion::getVersionNo, versionNo));
        for (OrgPositionVersion row : positionRows) {
            restorePosition(JsonUtil.toMap(row.getSnapshot()));
        }
        for (OrgGradeVersion row : gradeRows) {
            restoreGrade(JsonUtil.toMap(row.getSnapshot()));
        }
        stateService.activate(versionNo);
        LocalDate snapshotDate = positionRows.isEmpty()
                ? gradeRows.get(0).getSnapshotDate() : positionRows.get(0).getSnapshotDate();
        LocalDate effective = positionRows.isEmpty()
                ? gradeRows.get(0).getEffectiveFrom() : positionRows.get(0).getEffectiveFrom();
        return new OrgVersionSummary(versionNo, snapshotDate.toString(),
                positionRows.size(), gradeRows.size(), OrgVersionStatus.ACTIVE.name(),
                str(effective));
    }

    private OrgVersionStatus normalized(String status) {
        OrgVersionStatus parsed = OrgVersionStatus.from(status);
        return parsed == null ? OrgVersionStatus.ACTIVE : parsed;
    }

    /** 恢复岗位主数据（含空值显式覆盖）与挂靠部门（先删后插）。 */
    private void restorePosition(Map<String, Object> snap) {
        long id = longOf(snap.get("id"));
        String name = str(snap.get("name"));
        String code = str(snap.get("code"));
        Integer status = intOf(snap.get("status"));
        String jobFamily = str(snap.get("jobFamily"));
        if (positionMapper.selectById(id) == null) {
            OrgPosition e = new OrgPosition();
            e.setId(id);
            e.setName(name);
            e.setCode(code);
            e.setStatus(status);
            e.setJobFamily(jobFamily);
            positionMapper.insert(e);
        } else {
            positionMapper.update(null, new UpdateWrapper<OrgPosition>()
                    .eq("id", id)
                    .set("name", name)
                    .set("code", code)
                    .set("status", status)
                    .set("job_family", jobFamily));
        }
        positionDeptMapper.delete(new LambdaQueryWrapper<OrgPositionDept>()
                .eq(OrgPositionDept::getPositionId, id));
        if (snap.get("deptIds") instanceof List<?> deptIds) {
            for (Object deptId : deptIds) {
                OrgPositionDept rel = new OrgPositionDept();
                rel.setPositionId(id);
                rel.setDeptId(longOf(deptId));
                positionDeptMapper.insert(rel);
            }
        }
    }

    /** 恢复职级主数据（含空值显式覆盖）。 */
    private void restoreGrade(Map<String, Object> snap) {
        long id = longOf(snap.get("id"));
        String name = str(snap.get("name"));
        String code = str(snap.get("code"));
        Integer status = intOf(snap.get("status"));
        Long gradeBandId = snap.get("gradeBandId") == null ? null : longOf(snap.get("gradeBandId"));
        if (gradeMapper.selectById(id) == null) {
            OrgGrade e = new OrgGrade();
            e.setId(id);
            e.setName(name);
            e.setCode(code);
            e.setStatus(status);
            e.setGradeBandId(gradeBandId);
            gradeMapper.insert(e);
        } else {
            gradeMapper.update(null, new UpdateWrapper<OrgGrade>()
                    .eq("id", id)
                    .set("name", name)
                    .set("code", code)
                    .set("status", status)
                    .set("grade_band_id", gradeBandId));
        }
    }

    private void insertVersion(String versionNo, LocalDate date, LocalDate effective, long bizId,
                               Map<String, Object> snap, boolean position) {
        String json = JsonUtil.toJson(snap);
        if (position) {
            OrgPositionVersion row = new OrgPositionVersion();
            row.setVersionNo(versionNo);
            row.setPositionId(bizId);
            row.setSnapshotDate(date);
            row.setSnapshot(json);
            row.setStatus(OrgVersionStatus.DRAFT.name());
            row.setEffectiveFrom(effective);
            positionVersionMapper.insert(row);
        } else {
            OrgGradeVersion row = new OrgGradeVersion();
            row.setVersionNo(versionNo);
            row.setGradeId(bizId);
            row.setSnapshotDate(date);
            row.setSnapshot(json);
            row.setStatus(OrgVersionStatus.DRAFT.name());
            row.setEffectiveFrom(effective);
            gradeVersionMapper.insert(row);
        }
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private long longOf(Object v) {
        return v instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(v));
    }

    private Integer intOf(Object v) {
        return v instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(v));
    }
}

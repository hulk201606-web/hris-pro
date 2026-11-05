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
import java.util.List;
import java.util.Map;

/**
 * 组织版本服务（批 8 #16 MVP：组织版本扩展到岗位/职级，docs/20 §4.3 差距收敛）。
 *
 * <p>选型：与 org_dept_history 同构的新表 org_position_version / org_grade_version
 * （+version_no 批次标识），不为 org_dept_history 加 type 维度（dept_id 语义会被破坏）。</p>
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

    public OrgVersionService(OrgPositionMapper positionMapper, OrgGradeMapper gradeMapper,
                             OrgPositionDeptMapper positionDeptMapper,
                             OrgPositionVersionMapper positionVersionMapper,
                             OrgGradeVersionMapper gradeVersionMapper) {
        this.positionMapper = positionMapper;
        this.gradeMapper = gradeMapper;
        this.positionDeptMapper = positionDeptMapper;
        this.positionVersionMapper = positionVersionMapper;
        this.gradeVersionMapper = gradeVersionMapper;
    }

    /** 创建组织版本：全量快照全部岗位（含挂靠部门）与职级，版本号 V+雪花 ID。 */
    @Transactional(rollbackFor = Exception.class)
    public OrgVersionSummary create() {
        String versionNo = "V" + IdWorker.getId();
        LocalDate date = LocalDate.now();

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
            insertVersion(versionNo, date, p.getId(), snap, true);
        }

        List<OrgGrade> grades = gradeMapper.selectList(null);
        for (OrgGrade g : grades) {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("id", g.getId());
            snap.put("name", g.getName());
            snap.put("code", g.getCode());
            snap.put("gradeBandId", g.getGradeBandId());
            snap.put("status", g.getStatus());
            insertVersion(versionNo, date, g.getId(), snap, false);
        }
        return new OrgVersionSummary(versionNo, date.toString(), positions.size(), grades.size());
    }

    /** 版本列表（按创建先后倒序）。 */
    public List<OrgVersionSummary> list() {
        Map<String, List<OrgPositionVersion>> byVersion = new LinkedHashMap<>();
        for (OrgPositionVersion row : positionVersionMapper.selectList(null)) {
            byVersion.computeIfAbsent(row.getVersionNo(), k -> new ArrayList<>()).add(row);
        }
        Map<String, Long> gradeCounts = new LinkedHashMap<>();
        for (OrgGradeVersion row : gradeVersionMapper.selectList(null)) {
            gradeCounts.merge(row.getVersionNo(), 1L, Long::sum);
        }
        return byVersion.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().get(0).getId(),
                        Comparator.reverseOrder()))
                .map(e -> new OrgVersionSummary(e.getKey(),
                        e.getValue().get(0).getSnapshotDate().toString(),
                        e.getValue().size(), gradeCounts.getOrDefault(e.getKey(), 0L)))
                .toList();
    }

    /** 版本查看：返回该版本的岗位/职级两个维度快照内容。 */
    public OrgVersionDetail get(String versionNo) {
        List<Map<String, Object>> positions = positionVersionMapper.selectList(
                        new LambdaQueryWrapper<OrgPositionVersion>()
                                .eq(OrgPositionVersion::getVersionNo, versionNo))
                .stream().map(r -> JsonUtil.toMap(r.getSnapshot())).toList();
        List<Map<String, Object>> grades = gradeVersionMapper.selectList(
                        new LambdaQueryWrapper<OrgGradeVersion>()
                                .eq(OrgGradeVersion::getVersionNo, versionNo))
                .stream().map(r -> JsonUtil.toMap(r.getSnapshot())).toList();
        if (positions.isEmpty() && grades.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织版本不存在");
        }
        String snapshotDate = positionVersionMapper.selectOne(
                new LambdaQueryWrapper<OrgPositionVersion>()
                        .eq(OrgPositionVersion::getVersionNo, versionNo)
                        .last("LIMIT 1")).getSnapshotDate().toString();
        return new OrgVersionDetail(versionNo, snapshotDate, positions, grades);
    }

    /**
     * 版本回滚：按快照恢复岗位/职级主数据与岗位挂靠部门（upsert，按 ID 定位）。
     * MVP 局限：不级联员工归属重排；版本后新增的岗位/职级不删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrgVersionSummary rollback(String versionNo) {
        List<OrgPositionVersion> positionRows = positionVersionMapper.selectList(
                new LambdaQueryWrapper<OrgPositionVersion>()
                        .eq(OrgPositionVersion::getVersionNo, versionNo));
        List<OrgGradeVersion> gradeRows = gradeVersionMapper.selectList(
                new LambdaQueryWrapper<OrgGradeVersion>()
                        .eq(OrgGradeVersion::getVersionNo, versionNo));
        if (positionRows.isEmpty() && gradeRows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织版本不存在");
        }
        for (OrgPositionVersion row : positionRows) {
            restorePosition(JsonUtil.toMap(row.getSnapshot()));
        }
        for (OrgGradeVersion row : gradeRows) {
            restoreGrade(JsonUtil.toMap(row.getSnapshot()));
        }
        return new OrgVersionSummary(versionNo,
                positionRows.isEmpty()
                        ? gradeRows.get(0).getSnapshotDate().toString()
                        : positionRows.get(0).getSnapshotDate().toString(),
                positionRows.size(), gradeRows.size());
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

    private void insertVersion(String versionNo, LocalDate date, long bizId,
                               Map<String, Object> snap, boolean position) {
        String json = JsonUtil.toJson(snap);
        if (position) {
            OrgPositionVersion row = new OrgPositionVersion();
            row.setVersionNo(versionNo);
            row.setPositionId(bizId);
            row.setSnapshotDate(date);
            row.setSnapshot(json);
            positionVersionMapper.insert(row);
        } else {
            OrgGradeVersion row = new OrgGradeVersion();
            row.setVersionNo(versionNo);
            row.setGradeId(bizId);
            row.setSnapshotDate(date);
            row.setSnapshot(json);
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

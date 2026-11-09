package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hris.org.entity.OrgGrade;
import com.hris.org.entity.OrgPosition;
import com.hris.org.entity.OrgPositionDept;
import com.hris.org.mapper.OrgGradeMapper;
import com.hris.org.mapper.OrgPositionDeptMapper;
import com.hris.org.mapper.OrgPositionMapper;

import java.util.List;
import java.util.Map;

/**
 * 组织版本回滚的主数据恢复工具（ORG-F-004）：按版本快照 upsert 岗位/职级与岗位挂靠部门。
 *
 * <p>从 OrgVersionService 拆分而来（单文件 ≤300 行门禁），mapper 由调用方传入，
 * 因此宿主构造器签名保持不变。快照语义：空值也显式覆盖（回滚即还原当时的空字段）。</p>
 */
final class OrgVersionRestorer {

    private OrgVersionRestorer() {
    }

    /** 恢复岗位主数据（含空值显式覆盖）与挂靠部门（先删后插）。 */
    static void restorePosition(OrgPositionMapper positionMapper, OrgPositionDeptMapper positionDeptMapper,
                                Map<String, Object> snap) {
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
    static void restoreGrade(OrgGradeMapper gradeMapper, Map<String, Object> snap) {
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

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static long longOf(Object v) {
        return v instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(v));
    }

    private static Integer intOf(Object v) {
        return v instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(v));
    }
}

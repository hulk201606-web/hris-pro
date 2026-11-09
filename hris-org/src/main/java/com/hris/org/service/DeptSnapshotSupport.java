package com.hris.org.service;

import com.hris.org.entity.OrgDept;
import com.hris.org.entity.OrgDeptHistory;
import com.hris.org.mapper.OrgDeptHistoryMapper;
import com.hris.org.util.JsonUtil;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 部门历史快照（ORG-F-004）：写 org_dept_history 与快照字段序列化。
 * 从 DeptService 拆分而来（单文件 ≤300 行门禁），mapper 由调用方传入。
 */
final class DeptSnapshotSupport {

    private DeptSnapshotSupport() {
    }

    /** 写一条当日历史快照（更新/删除前调用，保留审计轨迹）。 */
    static void writeSnapshot(OrgDeptHistoryMapper deptHistoryMapper, OrgDept dept) {
        OrgDeptHistory history = new OrgDeptHistory();
        history.setDeptId(dept.getId());
        history.setSnapshotDate(LocalDate.now());
        history.setSnapshot(JsonUtil.toJson(snapshotMap(dept)));
        deptHistoryMapper.insert(history);
    }

    /** 部门快照字段（按回滚所需的最小必要集）。 */
    static Map<String, Object> snapshotMap(OrgDept dept) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("id", dept.getId());
        snap.put("parentId", dept.getParentId());
        snap.put("path", dept.getPath());
        snap.put("name", dept.getName());
        snap.put("code", dept.getCode());
        snap.put("managerEmpId", dept.getManagerEmpId());
        snap.put("effectiveFrom", dept.getEffectiveFrom() == null ? null : dept.getEffectiveFrom().toString());
        snap.put("effectiveTo", dept.getEffectiveTo() == null ? null : dept.getEffectiveTo().toString());
        snap.put("status", dept.getStatus());
        return snap;
    }
}

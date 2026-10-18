package com.hris.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 部门历史快照（每日/变更快照，回溯）。snapshot 为 JSON 字符串。
 */
@TableName("org_dept_history")
public class OrgDeptHistory extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 部门 ID（逻辑外键 org_dept.id） */
    private Long deptId;

    /** 快照日期 */
    private LocalDate snapshotDate;

    /** 部门快照（树/属性/负责人，JSON） */
    private String snapshot;

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(String snapshot) {
        this.snapshot = snapshot;
    }
}

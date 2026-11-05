package com.hris.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 组织版本-职级快照（批 8 #16 MVP：组织版本扩展到职级，与 org_dept_history 同构 + version_no 批次标识）。
 */
@TableName("org_grade_version")
public class OrgGradeVersion extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 组织版本号（同批次快照共享，如 V+雪花 ID） */
    private String versionNo;

    /** 职级 ID（逻辑外键 org_grade.id） */
    private Long gradeId;

    /** 快照日期 */
    private LocalDate snapshotDate;

    /** 职级快照（主数据，JSON） */
    private String snapshot;

    /** 版本状态 DRAFT/ACTIVE/SUPERSEDED（批 11 #23：同批次快照同值） */
    private String status;

    /** 计划生效日（未到该日期为草稿，不对外可见） */
    private LocalDate effectiveFrom;

    public String getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(String versionNo) {
        this.versionNo = versionNo;
    }

    public Long getGradeId() {
        return gradeId;
    }

    public void setGradeId(Long gradeId) {
        this.gradeId = gradeId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }
}

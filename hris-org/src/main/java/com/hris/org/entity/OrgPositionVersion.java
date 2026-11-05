package com.hris.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 组织版本-岗位快照（批 8 #16 MVP：组织版本扩展到岗位，与 org_dept_history 同构 + version_no 批次标识）。
 */
@TableName("org_position_version")
public class OrgPositionVersion extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 组织版本号（同批次快照共享，如 V+雪花 ID） */
    private String versionNo;

    /** 岗位 ID（逻辑外键 org_position.id） */
    private Long positionId;

    /** 快照日期 */
    private LocalDate snapshotDate;

    /** 岗位快照（主数据+挂靠部门，JSON） */
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

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
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

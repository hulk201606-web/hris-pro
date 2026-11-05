package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 期间关账记录（cmp_period_lock，CMP-F-020）。
 *
 * <p>独立于 run 级 LOCK 状态的期间级锁：记录谁锁的（locked_by）、何时锁（locked_at）、
 * 解锁理由（unlock_reason）。历史多锁并存（uk = tenant + period + locked_at），
 * ACTIVE 至多一条由应用层保证（lock 前置检查）。</p>
 */
@TableName("cmp_period_lock")
public class PeriodLock extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 周期编码（如 2026-01） */
    private String periodCode;

    /** 状态 ACTIVE/RELEASED */
    private String status;

    /** 关账人 ID */
    private Long lockedBy;

    /** 关账时间（UTC） */
    private LocalDateTime lockedAt;

    /** 解锁人 ID（NULL=未解锁） */
    private Long releasedBy;

    /** 解锁时间（UTC，NULL=未解锁） */
    private LocalDateTime releasedAt;

    /** 解锁理由（NULL=未解锁） */
    private String unlockReason;

    public String getPeriodCode() {
        return periodCode;
    }

    public void setPeriodCode(String periodCode) {
        this.periodCode = periodCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(Long lockedBy) {
        this.lockedBy = lockedBy;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }

    public Long getReleasedBy() {
        return releasedBy;
    }

    public void setReleasedBy(Long releasedBy) {
        this.releasedBy = releasedBy;
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(LocalDateTime releasedAt) {
        this.releasedAt = releasedAt;
    }

    public String getUnlockReason() {
        return unlockReason;
    }

    public void setUnlockReason(String unlockReason) {
        this.unlockReason = unlockReason;
    }
}

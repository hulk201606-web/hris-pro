package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 日考勤月结锁（域：考勤，分层：实体/DO，批11#19）。
 *
 * <p>一个期间（YYYY-MM）同时最多一条 ACTIVE 记录（应用层保证）；解锁不改写历史行，
 * 仅把状态置 RELEASED 并记录解锁人/时间/理由，因此 uk(tenant_id, period, closed_at)
 * 允许同期间多次加锁留痕。</p>
 */
@TableName("att_daily_close")
public class AttDailyClose extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 状态：已锁定 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态：已解锁 */
    public static final String STATUS_RELEASED = "RELEASED";

    /** 期间（YYYY-MM） */
    private String period;

    /** 状态 ACTIVE/RELEASED */
    private String status;

    /** 锁定操作人员工 ID */
    private Long closedBy;

    /** 锁定时间（UTC，uk 组成部分） */
    private LocalDateTime closedAt;

    /** 解锁操作人员工 ID */
    private Long releasedBy;

    /** 解锁时间（UTC） */
    private LocalDateTime releasedAt;

    /** 解锁理由（必填） */
    private String releaseReason;

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(Long closedBy) {
        this.closedBy = closedBy;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
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

    public String getReleaseReason() {
        return releaseReason;
    }

    public void setReleaseReason(String releaseReason) {
        this.releaseReason = releaseReason;
    }
}

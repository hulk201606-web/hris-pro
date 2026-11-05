package com.hris.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 报表订阅（sys_report_subscription，PLT-F-005）。
 *
 * <p>channels/recipients 以 JSON 字符串落库（MySQL JSON / PG JSONB），
 * 由服务层解析为列表；启用时同步注册调度中心任务，停用即下线任务。</p>
 */
@TableName("sys_report_subscription")
public class SysReportSubscription extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 报表定义 ID */
    private Long reportDefId;

    /** cron 表达式 */
    private String cronExpr;

    /** 通知渠道 JSON 数组 */
    private String channels;

    /** 接收人 JSON 数组 */
    private String recipients;

    /** 状态 1=启用 0=停用 */
    private Integer status;

    public Long getReportDefId() {
        return reportDefId;
    }

    public void setReportDefId(Long reportDefId) {
        this.reportDefId = reportDefId;
    }

    public String getCronExpr() {
        return cronExpr;
    }

    public void setCronExpr(String cronExpr) {
        this.cronExpr = cronExpr;
    }

    public String getChannels() {
        return channels;
    }

    public void setChannels(String channels) {
        this.channels = channels;
    }

    public String getRecipients() {
        return recipients;
    }

    public void setRecipients(String recipients) {
        this.recipients = recipients;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

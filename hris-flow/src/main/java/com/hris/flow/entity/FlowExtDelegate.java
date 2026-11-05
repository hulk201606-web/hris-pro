package com.hris.flow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 审批委托代理（flow_ext_delegate，FLW-F-009）。
 *
 * <p>from_user 将审批委托给 to_user；proc_def_key 为 NULL 表示全部流程。
 * 生效期内 to_user 的待办并入 from_user 的任务（见 TaskQueryService.todo）。</p>
 */
@TableName("flow_ext_delegate")
public class FlowExtDelegate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 委托发起人用户 ID（网关透传 userId，逻辑外键 auth_user.id） */
    private Long fromUser;

    /** 被委托人用户 ID（网关透传 userId，逻辑外键 auth_user.id） */
    private Long toUser;

    /** 流程定义 Key（NULL=全部流程） */
    private String procDefKey;

    /** 生效开始时间 */
    private LocalDateTime startAt;

    /** 生效结束时间 */
    private LocalDateTime endAt;

    /** 状态 ACTIVE/ENDED */
    private String status;

    /** 委托原因 */
    private String reason;

    public Long getFromUser() {
        return fromUser;
    }

    public void setFromUser(Long fromUser) {
        this.fromUser = fromUser;
    }

    public Long getToUser() {
        return toUser;
    }

    public void setToUser(Long toUser) {
        this.toUser = toUser;
    }

    public String getProcDefKey() {
        return procDefKey;
    }

    public void setProcDefKey(String procDefKey) {
        this.procDefKey = procDefKey;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

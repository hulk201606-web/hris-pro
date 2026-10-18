package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 加班单（att_overtime_request）。事前审批与事后补录双模式。
 *
 * <p>状态流转：DRAFT → PROCESSING → APPROVED / REJECTED / CANCELLED（docs/03-03 ATT-F-016）。</p>
 *
 * <p>注意：DDL 无倍率列与加班类型列，倍率按工作日/休息日/法定节假日在查询时派生，
 * 加班模式（事前/补录）落在 reason 前缀约定之外则由调用方以 mode 入参传入后写入 reason，
 * MVP 不新增列（见 OPEN-DECISIONS）。</p>
 */
@TableName("att_overtime_request")
public class OvertimeRequest extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    /** 业务单号（流程幂等键） */
    private String bizKey;

    /** 加班开始时间（UTC） */
    private LocalDateTime startTime;

    /** 加班结束时间（UTC） */
    private LocalDateTime endTime;

    /** 加班时长（小时） */
    private BigDecimal hours;

    /** 加班事由 */
    private String reason;

    /** 状态 DRAFT/PROCESSING/APPROVED/REJECTED/CANCELLED */
    private String status;

    /** 流程实例 ID */
    private String procInstId;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProcInstId() {
        return procInstId;
    }

    public void setProcInstId(String procInstId) {
        this.procInstId = procInstId;
    }
}

package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 补卡申请（域：考勤，分层：实体/DO）。缺卡员工申请补录上下班打卡，走审批流后生效，状态机 DRAFT→APPROVING→APPROVED/REJECTED。
 */
@TableName("att_fix_request")
public class FixRequest extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    /** 业务单号（流程幂等键） */
    private String bizKey;

    /** 补卡日期 */
    private LocalDate workDate;

    /** 补卡时间（UTC） */
    private LocalDateTime fixTime;

    /** 补卡类型 IN/OUT */
    private String type;

    /** 补卡原因 */
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

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public LocalDateTime getFixTime() {
        return fixTime;
    }

    public void setFixTime(LocalDateTime fixTime) {
        this.fixTime = fixTime;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

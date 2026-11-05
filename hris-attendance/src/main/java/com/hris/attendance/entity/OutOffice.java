package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 外出/居家办公申请（域：考勤，分层：实体/DO，ATT-F-018）。
 *
 * <p>与出差单（att_trip_request）同属「离岗但非请假」的出勤口径：审批通过后
 * 区间内不判缺勤/旷工。区别在于外出/居家单通常当日发生、时效短，审批走
 * 权限点直批（不走 Flowable），因此无 biz_key / proc_inst_id 列。</p>
 */
@TableName("att_out_office")
public class OutOffice extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    /** 类型：OUT=外出 / WFH=居家办公 */
    private String type;

    /** 开始时间（UTC） */
    private LocalDateTime startTime;

    /** 结束时间（UTC） */
    private LocalDateTime endTime;

    /** 事由 */
    private String reason;

    /** 状态：PROCESSING=审批中 / APPROVED=已通过 / REJECTED=已驳回 */
    private String status;

    /** 审批人员工 ID（审批通过/驳回时回填） */
    private Long approverId;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }
}

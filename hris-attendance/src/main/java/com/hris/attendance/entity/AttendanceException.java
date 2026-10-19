package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 考勤异常单（域：考勤，分层：实体/DO）。记录日结判定的迟到/早退/缺卡/旷工等异常，状态机 OPEN→RESOLVED/IGNORED，月结锁账前必须清零。
 */
@TableName("att_exception")
public class AttendanceException extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    /** 工作日 */
    private LocalDate workDate;

    /** 异常类型 LATE/EARLY/MISSING_PUNCH/ABSENT */
    private String exceptionType;

    /** 状态 OPEN/RESOLVED/IGNORED */
    private String status;

    /** 异常说明 */
    private String description;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

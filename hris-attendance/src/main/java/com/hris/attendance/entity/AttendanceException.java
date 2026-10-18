package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 考勤异常（att_exception）。日考勤计算产出的待处理事项。
 *
 * <p>注意：本类是业务实体（对应 att_exception 表），并非异常类型，命名沿用 docs/03-03 §3
 * 核心实体清单中的 AttendanceException。</p>
 *
 * <p>idx(tenant_id, emp_id, work_date, status) 支撑异常工作台按人按日筛选（docs/12 §5.4）。</p>
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

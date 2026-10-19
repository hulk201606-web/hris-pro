package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * 报名记录（域：绩效/培训，分层：实体/DO）。员工报名某培训班次，状态机 ENROLLED→COMPLETED/WITHDRAWN。
 */
@TableName("prf_enrollment")
public class Enrollment extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long sessionId;  // 场次ID(逻辑外键 prf_class_session.id)

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }
    private Long empId;  // 员工ID(逻辑外键 emp_employee.id)

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }
    private String status;  // 状态 ENROLLED/COMPLETED/CANCELLED

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

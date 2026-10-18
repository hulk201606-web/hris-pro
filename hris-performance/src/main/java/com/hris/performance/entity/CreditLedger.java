package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.math.BigDecimal;

/**
 * prf_credit_ledger（由 DDL 自动生成）
 */
@TableName("prf_credit_ledger")
public class CreditLedger extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long empId;  // 员工ID(逻辑外键 emp_employee.id)

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }
    private Long courseId;  // 课程ID(逻辑外键 prf_course.id)

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }
    private BigDecimal credits;  // 获得学时

    public BigDecimal getCredits() {
        return credits;
    }

    public void setCredits(BigDecimal credits) {
        this.credits = credits;
    }
}

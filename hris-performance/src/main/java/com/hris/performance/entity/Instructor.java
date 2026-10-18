package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * prf_instructor（由 DDL 自动生成）
 */
@TableName("prf_instructor")
public class Instructor extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long empId;  // 讲师员工ID(逻辑外键 emp_employee.id)

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }
}

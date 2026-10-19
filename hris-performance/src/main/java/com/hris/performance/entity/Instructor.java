package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * 讲师（域：绩效/培训，分层：实体/DO）。内部讲师主档（关联员工 ID）。
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

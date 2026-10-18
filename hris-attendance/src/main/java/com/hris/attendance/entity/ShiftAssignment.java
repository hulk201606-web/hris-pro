package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 排班（att_shift_assignment）。员工在某工作日绑定的班次。
 *
 * <p>uk(tenant_id, emp_id, work_date) 保证一人一日仅一条排班（docs/12 §5.4）。</p>
 */
@TableName("att_shift_assignment")
public class ShiftAssignment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    /** 班次 ID（逻辑外键 att_shift.id） */
    private Long shiftId;

    /** 工作日 */
    private LocalDate workDate;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public Long getShiftId() {
        return shiftId;
    }

    public void setShiftId(Long shiftId) {
        this.shiftId = shiftId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }
}

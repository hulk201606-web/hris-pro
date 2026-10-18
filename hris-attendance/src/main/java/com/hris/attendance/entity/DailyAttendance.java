package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日考勤结果（att_daily_attendance）。每日计算的最终归集，供月结与薪酬取数。
 *
 * <p>uk(tenant_id, emp_id, work_date) 保证一人一日一条结果，重算走 upsert（docs/12 §5.4）。</p>
 */
@TableName("att_daily_attendance")
public class DailyAttendance extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    /** 工作日 */
    private LocalDate workDate;

    /** 雇佣关系 ID（逻辑外键 emp_employment.id，日期归属版本） */
    private Long employmentId;

    /** 出勤状态 NORMAL/LATE/EARLY/ABSENT/LEAVE */
    private String attStatus;

    /** 实际打卡上班时间 */
    private LocalDateTime punchIn;

    /** 实际打卡下班时间 */
    private LocalDateTime punchOut;

    /** 请假时长（小时） */
    private BigDecimal leaveHours;

    /** 加班时长（小时） */
    private BigDecimal overtimeHours;

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

    public Long getEmploymentId() {
        return employmentId;
    }

    public void setEmploymentId(Long employmentId) {
        this.employmentId = employmentId;
    }

    public String getAttStatus() {
        return attStatus;
    }

    public void setAttStatus(String attStatus) {
        this.attStatus = attStatus;
    }

    public LocalDateTime getPunchIn() {
        return punchIn;
    }

    public void setPunchIn(LocalDateTime punchIn) {
        this.punchIn = punchIn;
    }

    public LocalDateTime getPunchOut() {
        return punchOut;
    }

    public void setPunchOut(LocalDateTime punchOut) {
        this.punchOut = punchOut;
    }

    public BigDecimal getLeaveHours() {
        return leaveHours;
    }

    public void setLeaveHours(BigDecimal leaveHours) {
        this.leaveHours = leaveHours;
    }

    public BigDecimal getOvertimeHours() {
        return overtimeHours;
    }

    public void setOvertimeHours(BigDecimal overtimeHours) {
        this.overtimeHours = overtimeHours;
    }
}

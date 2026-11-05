package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 考勤组（域：考勤，分层：实体/DO）。按部门/班组划分的打卡管理单元，员工归属考勤组后按组规则打卡。
 */
@TableName("att_group")
public class AttGroup extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 班组名称 */
    private String name;

    /** 班组编码（租户内唯一） */
    private String code;

    /** 班组长员工 ID（逻辑外键 emp_employee.id） */
    private Long leaderEmpId;

    /**
     * 绑定的节假日历年度（B-5）。空 = 不绑定，日考勤按全量日历判定；
     * 绑定后仅该年度的 att_holiday 配置对本组生效，其余年度回退星期推断。
     */
    private Integer holidayCalendarYear;

    public Integer getHolidayCalendarYear() {
        return holidayCalendarYear;
    }

    public void setHolidayCalendarYear(Integer holidayCalendarYear) {
        this.holidayCalendarYear = holidayCalendarYear;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getLeaderEmpId() {
        return leaderEmpId;
    }

    public void setLeaderEmpId(Long leaderEmpId) {
        this.leaderEmpId = leaderEmpId;
    }
}

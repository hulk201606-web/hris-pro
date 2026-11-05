package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 法定节假日与调班日（域：考勤，分层：实体/DO）。
 *
 * <p>两个方向都要能表达，缺一不可：</p>
 * <ul>
 *   <li>HOLIDAY —— 法定节假日：本来是工作日，但放假</li>
 *   <li>WORKDAY —— 调班补班：本来是休息日，但要上班（如国庆前的周六）</li>
 * </ul>
 *
 * <p>只记「哪些天放假」是不够的——调休制度下有些周末要上班，
 * 若只维护节假日而不知道补班日，日考勤会把补班日误判为休息日、漏算缺勤。</p>
 *
 * <p>唯一索引 (tenant_id, holiday_date) 保证同一天只有一种定义，
 * 不存在「既是节假日又是补班日」的自相矛盾数据。</p>
 */
@TableName("att_holiday")
public class Holiday extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 日历年度（= holiday_date 的年份，服务端派生，供考勤组按年绑定日历） */
    private Integer calendarYear;

    /** 日期 */
    private LocalDate holidayDate;

    /** 名称，如「春节」「国庆调休」 */
    private String name;

    /** 日期类型：HOLIDAY=法定节假日（放假） / WORKDAY=调班补班（上班） */
    private String dayType;

    /** 备注 */
    private String remark;

    public Integer getCalendarYear() {
        return calendarYear;
    }

    public void setCalendarYear(Integer calendarYear) {
        this.calendarYear = calendarYear;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public void setHolidayDate(LocalDate holidayDate) {
        this.holidayDate = holidayDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDayType() {
        return dayType;
    }

    public void setDayType(String dayType) {
        this.dayType = dayType;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}

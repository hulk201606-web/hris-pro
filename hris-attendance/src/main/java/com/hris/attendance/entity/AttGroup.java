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

    /** 工作制：FIXED=固定（缺省）/ FLEXIBLE=弹性（批12#27） */
    private String workMode;

    /** 组时区（IANA，缺省 Asia/Shanghai）：决定「今天」与迟到/早退比较基准 */
    private String timezone;

    /** 弹性区间（分钟，FLEXIBLE 生效，0=关闭） */
    private Integer timeWindowMinutes;

    /** 绑定考勤围栏 ID（逻辑外键 att_location.id，空=不校验围栏） */
    private Long locationId;

    /** 是否强制定位打卡 1=是（缺省）0=否；仅组绑围栏时生效 */
    private Integer requireLocation;

    public String getWorkMode() {
        return workMode;
    }

    public void setWorkMode(String workMode) {
        this.workMode = workMode;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Integer getTimeWindowMinutes() {
        return timeWindowMinutes;
    }

    public void setTimeWindowMinutes(Integer timeWindowMinutes) {
        this.timeWindowMinutes = timeWindowMinutes;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Integer getRequireLocation() {
        return requireLocation;
    }

    public void setRequireLocation(Integer requireLocation) {
        this.requireLocation = requireLocation;
    }

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

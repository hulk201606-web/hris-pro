package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 打卡记录（域：考勤，分层：实体/DO）。员工每次上下班打卡的原始流水（时间/来源/定位/设备），是日结的数据源头。
 */
@TableName("att_punch_record")
public class PunchRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    /** 打卡时间（UTC，分区键） */
    private LocalDateTime punchTime;

    /** 打卡类型 IN/OUT */
    private String type;

    /** 打卡来源 APP/DEVICE/GPS */
    private String source;

    /** 打卡地点 */
    private String location;

    /** 设备标识 */
    private String deviceId;

    /** 班组 ID（逻辑外键 att_group.id） */
    private Long attGroupId;

    /** 状态 0=正常 1=异常 */
    private Integer status;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public LocalDateTime getPunchTime() {
        return punchTime;
    }

    public void setPunchTime(LocalDateTime punchTime) {
        this.punchTime = punchTime;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Long getAttGroupId() {
        return attGroupId;
    }

    public void setAttGroupId(Long attGroupId) {
        this.attGroupId = attGroupId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

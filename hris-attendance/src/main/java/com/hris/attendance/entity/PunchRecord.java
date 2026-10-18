package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 打卡记录（att_punch_record）。按 punch_time 月分区存储（docs/12 §5.4）。
 *
 * <p>幂等：uk(tenant_id, emp_id, punch_time) 保证同一员工同一时刻只有一条记录，
 * 高峰期重复提交由唯一键冲突兜底（docs/09 打卡洪峰）。</p>
 *
 * <p>注意：本表为分区表，pk 为 (id, punch_time)，查询必须带 punch_time 区间以命中分区。</p>
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

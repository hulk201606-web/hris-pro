package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalTime;

/**
 * 班次（att_shift）。固定/弹性班次，含上下班时刻。
 *
 * <p>跨天班次判定：end_time <= start_time 视为次日下班（docs/03-03 ATT-F-002）。</p>
 */
@TableName("att_shift")
public class Shift extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 班次名称 */
    private String name;

    /** 班次编码（租户内唯一） */
    private String code;

    /** 上班时间 */
    private LocalTime startTime;

    /** 下班时间 */
    private LocalTime endTime;

    /** 是否弹性 1=是 0=否 */
    private Integer flexible;

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

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Integer getFlexible() {
        return flexible;
    }

    public void setFlexible(Integer flexible) {
        this.flexible = flexible;
    }
}

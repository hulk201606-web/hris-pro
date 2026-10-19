package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.math.BigDecimal;

/**
 * 课程（域：绩效/培训，分层：实体/DO）。培训课程主数据（名称/学时）。
 */
@TableName("prf_course")
public class Course extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private String name;  // 课程名称

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    private String code;  // 课程编码

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
    private BigDecimal hours;  // 课程学时

    public BigDecimal getHours() {
        return hours;
    }

    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }
}

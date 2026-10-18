package com.hris.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 职级带（广播表，如 P4-P9 / M1-M5）。
 */
@TableName("org_grade_band")
public class OrgGradeBand extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 职级带名称 */
    private String name;

    /** 职级带编码（租户内唯一） */
    private String code;

    /** 带内最低职级编码 */
    private String minGrade;

    /** 带内最高职级编码 */
    private String maxGrade;

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

    public String getMinGrade() {
        return minGrade;
    }

    public void setMinGrade(String minGrade) {
        this.minGrade = minGrade;
    }

    public String getMaxGrade() {
        return maxGrade;
    }

    public void setMaxGrade(String maxGrade) {
        this.maxGrade = maxGrade;
    }
}

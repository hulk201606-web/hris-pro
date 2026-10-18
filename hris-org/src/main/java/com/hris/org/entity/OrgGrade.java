package com.hris.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 职级（ORG-F-008）。
 */
@TableName("org_grade")
public class OrgGrade extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 职级名称 */
    private String name;

    /** 职级编码（租户内唯一） */
    private String code;

    /** 所属职级带 ID（逻辑外键 org_grade_band.id） */
    private Long gradeBandId;

    /** 状态 1=有效 0=失效 */
    private Integer status;

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

    public Long getGradeBandId() {
        return gradeBandId;
    }

    public void setGradeBandId(Long gradeBandId) {
        this.gradeBandId = gradeBandId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

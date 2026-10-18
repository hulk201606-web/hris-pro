package com.hris.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 岗位（ORG-F-006）。
 */
@TableName("org_position")
public class OrgPosition extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 岗位名称 */
    private String name;

    /** 岗位编码（租户内唯一） */
    private String code;

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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

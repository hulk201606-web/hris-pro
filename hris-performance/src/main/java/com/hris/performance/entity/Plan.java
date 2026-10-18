package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * prf_plan（由 DDL 自动生成）
 */
@TableName("prf_plan")
public class Plan extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private String name;  // 方案名称

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    private String code;  // 方案编码

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
    private Integer status;  // 状态 1=启用 0=停用

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

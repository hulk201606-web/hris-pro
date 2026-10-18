package com.hris.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * rcu_channel（由 DDL 自动生成）
 */
@TableName("rcu_channel")
public class Channel extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private String name;  // 渠道名称

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    private String code;  // 渠道编码

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}

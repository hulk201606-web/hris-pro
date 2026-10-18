package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * cmp_pay_group（由 DDL 自动生成）
 */
@TableName("cmp_pay_group")
public class PayGroup extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private String name;  // 薪资组名称

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    private String code;  // 薪资组编码

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
    private String payCycle;  // 发薪周期 MONTHLY/BIWEEKLY

    public String getPayCycle() {
        return payCycle;
    }

    public void setPayCycle(String payCycle) {
        this.payCycle = payCycle;
    }
}

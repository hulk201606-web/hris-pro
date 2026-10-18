package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * cmp_pay_item（由 DDL 自动生成）
 */
@TableName("cmp_pay_item")
public class PayItem extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private String name;  // 薪酬项名称

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    private String code;  // 薪酬项编码

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
    private String formula;  // 计算公式(json)

    public String getFormula() {
        return formula;
    }

    public void setFormula(String formula) {
        this.formula = formula;
    }
    private Integer status;  // 状态 1=启用 0=停用

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

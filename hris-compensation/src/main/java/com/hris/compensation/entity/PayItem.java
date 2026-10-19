package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * 薪资项目（域：薪酬，分层：实体/DO）。工资条上的每一项（基本工资/绩效奖金/社保/个税等），含计算公式。
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

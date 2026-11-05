package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * 薪酬项（域：薪酬，分层：实体/DO）。工资条上的每一项（基本工资/绩效奖金/社保/个税等），
 * 按 pay_group 维度定义，含 Aviator 计算公式（CMP-F-021 沙箱求值）与类别/求值顺序。
 */
@TableName("cmp_pay_item")
public class PayItem extends BaseEntity {

    private static final long serialVersionUID = 1L;
    /** 类别：收入项 */
    public static final String CATEGORY_EARNING = "EARNING";
    /** 类别：扣除项 */
    public static final String CATEGORY_DEDUCTION = "DEDUCTION";
    /** 类别：个税项 */
    public static final String CATEGORY_TAX = "TAX";
    /** 类别：实发项 */
    public static final String CATEGORY_NET = "NET";

    private Long payGroupId;  // 薪资组ID(逻辑外键 cmp_pay_group.id),0=全局

    public Long getPayGroupId() {
        return payGroupId;
    }

    public void setPayGroupId(Long payGroupId) {
        this.payGroupId = payGroupId;
    }
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
    private String category;  // 类别 EARNING/DEDUCTION/TAX/NET

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
    private Integer seq;  // 求值顺序(小者优先)

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }
    private String formula;  // 计算公式(Aviator 表达式,可空;NET 缺省=sum(EARNING)-sum(DEDUCTION)-sum(TAX))

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

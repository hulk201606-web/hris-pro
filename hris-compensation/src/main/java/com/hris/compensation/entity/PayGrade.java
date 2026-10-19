package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.math.BigDecimal;

/**
 * 薪级（域：薪酬，分层：实体/DO）。职级体系中的薪资区间（min~max），定薪时校验定薪额落在区间内。
 */
@TableName("cmp_pay_grade")
public class PayGrade extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private String name;  // 薪级名称

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    private String code;  // 薪级编码

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
    private BigDecimal minSalary;  // 下限

    public BigDecimal getMinSalary() {
        return minSalary;
    }

    public void setMinSalary(BigDecimal minSalary) {
        this.minSalary = minSalary;
    }
    private BigDecimal maxSalary;  // 上限

    public BigDecimal getMaxSalary() {
        return maxSalary;
    }

    public void setMaxSalary(BigDecimal maxSalary) {
        this.maxSalary = maxSalary;
    }
}

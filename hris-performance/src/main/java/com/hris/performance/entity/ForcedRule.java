package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.math.BigDecimal;

/**
 * prf_forced_rule（由 DDL 自动生成）
 */
@TableName("prf_forced_rule")
public class ForcedRule extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private Long planId;  // 方案ID(逻辑外键 prf_plan.id)

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }
    private String grade;  // 等级(如 A/B/C/D)

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }
    private BigDecimal ratio;  // 比例(%)

    public BigDecimal getRatio() {
        return ratio;
    }

    public void setRatio(BigDecimal ratio) {
        this.ratio = ratio;
    }
}

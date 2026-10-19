package com.hris.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
import java.math.BigDecimal;

/**
 * 强制分布规则（域：绩效，分层：实体/DO）。绩效评级的强制分布比例（如 S 10%/A 20%），校准时执行。
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

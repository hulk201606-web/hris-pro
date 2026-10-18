package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 假种（att_leave_type）。年假/病假/事假等，按租户自定义。
 *
 * <p>rules 以 JSON 字符串存放额度与审批策略（带薪、证明要求、最小单位 0.5h/0.5d、
 * 司龄阶梯、有效期与结转上限），禁止在代码里硬编码折算公式（docs/03-03 §4）。</p>
 */
@TableName("att_leave_type")
public class LeaveType extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 假种名称 */
    private String name;

    /** 假种编码（租户内唯一） */
    private String code;

    /** 额度/审批规则（JSON 字符串） */
    private String rules;

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

    public String getRules() {
        return rules;
    }

    public void setRules(String rules) {
        this.rules = rules;
    }
}

package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 假期类型（域：考勤，分层：实体/DO）。定义年假/病假/事假等类型及其规则（带薪/无薪/上限天数）。
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

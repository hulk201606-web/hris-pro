package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 考勤班组（att_group）。按部门/人群划分，承载工作制、打卡方式与时区策略。
 *
 * <p>uk(tenant_id, code) 保证租户内班组编码唯一（docs/12 §5.4）。</p>
 */
@TableName("att_group")
public class AttGroup extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 班组名称 */
    private String name;

    /** 班组编码（租户内唯一） */
    private String code;

    /** 班组长员工 ID（逻辑外键 emp_employee.id） */
    private Long leaderEmpId;

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

    public Long getLeaderEmpId() {
        return leaderEmpId;
    }

    public void setLeaderEmpId(Long leaderEmpId) {
        this.leaderEmpId = leaderEmpId;
    }
}

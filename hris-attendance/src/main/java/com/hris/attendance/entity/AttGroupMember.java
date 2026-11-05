package com.hris.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 考勤组成员（域：考勤，分层：实体/DO）。员工与考勤班组的归属关联（批6#10），
 * 供节假日年度判定按「员工所在组并集」收敛、班组维度的考勤政策生效范围解析。
 *
 * <p>维护语义为全量覆盖（组内成员整体重设），删除走物理删除：
 * uk(tenant_id, group_id, emp_id) 不含 deleted，逻辑删残留会占住唯一键导致无法重新加入。</p>
 */
@TableName("att_group_member")
public class AttGroupMember extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 班组 ID（逻辑外键 att_group.id） */
    private Long groupId;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long empId;

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }
}

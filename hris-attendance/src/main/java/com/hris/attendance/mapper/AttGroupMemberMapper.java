package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.AttGroupMember;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 考勤组成员数据访问。
 */
@Mapper
public interface AttGroupMemberMapper extends BaseMapper<AttGroupMember> {

    /**
     * 按班组物理删除（绕过 @TableLogic）。
     *
     * <p>本表唯一键 (tenant_id, group_id, emp_id) 不含 deleted：若走逻辑删除，
     * 残留行仍占住唯一键，同员工无法重新加入班组（同 HolidayMapper 的 1062 实测结论）。
     * 归属关联是可重建的配置数据、无审计留存要求，全量覆盖语义下物理删除。
     * 租户条件由 TenantLineInnerInterceptor 自动注入，SQL 里不要手写 tenant_id。</p>
     */
    @Delete("DELETE FROM att_group_member WHERE group_id = #{groupId}")
    int physicalDeleteByGroup(@Param("groupId") Long groupId);

    /** 按班组 + 员工物理删除（单个移除成员）。 */
    @Delete("DELETE FROM att_group_member WHERE group_id = #{groupId} AND emp_id = #{empId}")
    int physicalDeleteOne(@Param("groupId") Long groupId, @Param("empId") Long empId);
}

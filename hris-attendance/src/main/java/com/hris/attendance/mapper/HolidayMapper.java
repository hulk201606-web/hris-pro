package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.Holiday;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 法定节假日与调班日数据访问。
 */
@Mapper
public interface HolidayMapper extends BaseMapper<Holiday> {

    /**
     * 物理删除（绕过 @TableLogic）。
     *
     * <p>本表有唯一键 (tenant_id, holiday_date)：若走逻辑删除，删除后的行仍占着
     * 唯一键，同一天永远不能再建记录，日历维护会直接卡死。实测过：逻辑删除后
     * 再插入同日期触发 1062。日历是配置数据、无审计留存要求，故物理删除。
     * 租户条件由 TenantLineInnerInterceptor 自动注入，SQL 里不要手写 tenant_id。</p>
     */
    @Delete("DELETE FROM att_holiday WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}

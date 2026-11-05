package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.OutOffice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外出/居家办公申请数据访问。租户条件由 TenantLineInnerInterceptor 自动注入。
 */
@Mapper
public interface OutOfficeMapper extends BaseMapper<OutOffice> {
}

package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.FixRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 补卡单数据访问。
 */
@Mapper
public interface FixRequestMapper extends BaseMapper<FixRequest> {
}

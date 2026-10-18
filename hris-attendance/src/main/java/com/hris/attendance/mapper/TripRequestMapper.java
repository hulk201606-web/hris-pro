package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.TripRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 出差单数据访问。
 */
@Mapper
public interface TripRequestMapper extends BaseMapper<TripRequest> {
}

package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.OvertimeRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 加班单数据访问。
 */
@Mapper
public interface OvertimeRequestMapper extends BaseMapper<OvertimeRequest> {
}

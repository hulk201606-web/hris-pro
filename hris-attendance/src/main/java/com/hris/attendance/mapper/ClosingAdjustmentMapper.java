package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.ClosingAdjustment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 月结调整项数据访问。
 */
@Mapper
public interface ClosingAdjustmentMapper extends BaseMapper<ClosingAdjustment> {
}

package com.hris.compensation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.compensation.entity.PayItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * cmp_pay_item 数据访问。
 */
@Mapper
public interface PayItemMapper extends BaseMapper<PayItem> {
}

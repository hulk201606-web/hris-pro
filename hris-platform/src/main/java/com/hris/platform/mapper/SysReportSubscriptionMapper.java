package com.hris.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.platform.entity.SysReportSubscription;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报表订阅数据访问。
 */
@Mapper
public interface SysReportSubscriptionMapper extends BaseMapper<SysReportSubscription> {
}

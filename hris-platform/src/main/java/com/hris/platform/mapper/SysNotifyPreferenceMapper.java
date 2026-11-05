package com.hris.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.platform.entity.SysNotifyPreference;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知渠道偏好数据访问（PLT-F-004）。
 */
@Mapper
public interface SysNotifyPreferenceMapper extends BaseMapper<SysNotifyPreference> {
}

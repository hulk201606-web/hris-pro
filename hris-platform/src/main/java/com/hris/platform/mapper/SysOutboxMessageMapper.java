package com.hris.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.platform.entity.SysOutboxMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 全局事件表数据访问（发件箱模式）。
 */
@Mapper
public interface SysOutboxMessageMapper extends BaseMapper<SysOutboxMessage> {
}

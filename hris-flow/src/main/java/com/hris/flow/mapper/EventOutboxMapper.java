package com.hris.flow.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.flow.entity.EventOutbox;
import org.apache.ibatis.annotations.Mapper;

/**
 * 事件出站表数据访问。
 *
 * <p>平台级无租户表（无 tenant_id 列），必须跳过 MyBatis-Plus 租户插件，否则 SQL 注入
 * tenant_id 条件导致 unknown column（见 EventOutbox 实体注释）。</p>
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface EventOutboxMapper extends BaseMapper<EventOutbox> {
}

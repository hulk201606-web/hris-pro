package com.hris.platform.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.platform.entity.SysSchedulerTaskRegistry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 调度任务注册数据访问。
 */
@Mapper
public interface SysSchedulerTaskRegistryMapper extends BaseMapper<SysSchedulerTaskRegistry> {

    /**
     * 装载全部租户的启用任务。
     *
     * <p>调度线程没有 HTTP 请求带来的租户上下文，租户插件会注入 tenant_id=0
     * 而查不到任何注册项，所以这里必须显式绕过租户插件。这是平台级调度器
     * 唯一合法的跨租户查询；业务数据访问仍一律走当前租户上下文。</p>
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_scheduler_task_registry WHERE deleted = 0 AND status = 1")
    List<SysSchedulerTaskRegistry> selectEnabledAcrossTenants();
}

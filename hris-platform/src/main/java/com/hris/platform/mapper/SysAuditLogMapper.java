package com.hris.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.platform.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志数据访问（只追加：仅 insert + 查询，无 update/delete 方法）。
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}

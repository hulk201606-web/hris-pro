package com.hris.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.employee.entity.ChangeAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字段变更审计数据访问。
 */
@Mapper
public interface ChangeAuditMapper extends BaseMapper<ChangeAudit> {
}

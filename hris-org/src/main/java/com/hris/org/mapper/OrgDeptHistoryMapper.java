package com.hris.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.org.entity.OrgDeptHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 部门历史快照数据访问。
 */
@Mapper
public interface OrgDeptHistoryMapper extends BaseMapper<OrgDeptHistory> {
}

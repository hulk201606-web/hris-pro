package com.hris.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.org.entity.OrgJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * 职务数据访问。
 */
@Mapper
public interface OrgJobMapper extends BaseMapper<OrgJob> {
}

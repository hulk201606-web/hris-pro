package com.hris.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.auth.entity.AuthRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色数据访问。
 */
@Mapper
public interface AuthRoleMapper extends BaseMapper<AuthRole> {
}

package com.hris.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.auth.entity.AuthDataScope;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据权限规则数据访问。
 */
@Mapper
public interface AuthDataScopeMapper extends BaseMapper<AuthDataScope> {
}

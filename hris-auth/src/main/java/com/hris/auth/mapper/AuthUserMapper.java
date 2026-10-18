package com.hris.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.auth.entity.AuthUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户账号数据访问。
 */
@Mapper
public interface AuthUserMapper extends BaseMapper<AuthUser> {
}

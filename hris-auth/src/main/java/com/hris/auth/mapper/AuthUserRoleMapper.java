package com.hris.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.auth.entity.AuthUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色关联数据访问。
 */
@Mapper
public interface AuthUserRoleMapper extends BaseMapper<AuthUserRole> {

    /**
     * 按用户 ID 查角色 ID 集合。
     */
    @Select("SELECT role_id FROM auth_user_role WHERE user_id = #{userId} AND deleted = 0")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);
}

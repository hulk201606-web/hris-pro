package com.hris.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.auth.entity.AuthRolePerm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限点数据访问。
 */
@Mapper
public interface AuthRolePermMapper extends BaseMapper<AuthRolePerm> {

    /**
     * 按角色 ID 集合查权限点编码（去重）。
     */
    @Select("<script>"
            + "SELECT DISTINCT perm_code FROM auth_role_perm "
            + "WHERE role_id IN "
            + "<foreach collection='roleIds' item='rid' open='(' separator=',' close=')'>#{rid}</foreach>"
            + " AND deleted = 0"
            + "</script>")
    List<String> selectPermCodesByRoleIds(@Param("roleIds") List<Long> roleIds);
}

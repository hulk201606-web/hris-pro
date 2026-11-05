package com.hris.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 字典引用探测数据访问（PLT-F-007 深化）。
 *
 * <p>表名/列名只允许来自 {@link com.hris.platform.service.DictItemService} 内的
 * 静态 curated 白名单映射（不接受任何用户输入），故使用 ${} 拼接是安全的；
 * 值与租户走 #{} 预编译参数。</p>
 */
@Mapper
public interface DictRefProbeMapper {

    /** 统计业务表中引用某字典值的行数（跨库表须带 schema 前缀，如 emp_db.emp_contract）。 */
    @Select("SELECT COUNT(*) FROM ${table} WHERE ${column} = #{value} "
            + "AND tenant_id = #{tenantId} AND deleted = 0")
    long countByValue(@Param("table") String table,
                      @Param("column") String column,
                      @Param("value") String value,
                      @Param("tenantId") Long tenantId);
}

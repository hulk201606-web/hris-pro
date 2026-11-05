package com.hris.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.employee.entity.EmpProfileChange;
import org.apache.ibatis.annotations.Mapper;

/**
 * 关键信息变更申请数据访问。
 */
@Mapper
public interface EmpProfileChangeMapper extends BaseMapper<EmpProfileChange> {
}

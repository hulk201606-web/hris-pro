package com.hris.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.employee.entity.EmpNoSeq;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工号序号段数据访问（分配时由 Service 追加 FOR UPDATE 行锁）。
 */
@Mapper
public interface EmpNoSeqMapper extends BaseMapper<EmpNoSeq> {
}

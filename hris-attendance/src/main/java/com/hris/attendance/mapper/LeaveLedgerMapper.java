package com.hris.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.attendance.entity.LeaveLedger;
import org.apache.ibatis.annotations.Mapper;

/**
 * 假期额度流水数据访问。
 */
@Mapper
public interface LeaveLedgerMapper extends BaseMapper<LeaveLedger> {
}

package com.hris.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.employee.entity.PendingTransaction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待生效事务队列数据访问。
 */
@Mapper
public interface PendingTransactionMapper extends BaseMapper<PendingTransaction> {
}

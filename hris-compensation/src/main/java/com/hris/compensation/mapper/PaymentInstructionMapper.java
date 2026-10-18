package com.hris.compensation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.compensation.entity.PaymentInstruction;
import org.apache.ibatis.annotations.Mapper;

/**
 * cmp_payment_instruction 数据访问。
 */
@Mapper
public interface PaymentInstructionMapper extends BaseMapper<PaymentInstruction> {
}

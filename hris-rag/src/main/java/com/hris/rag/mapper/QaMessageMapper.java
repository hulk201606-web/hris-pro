package com.hris.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.rag.entity.QaMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * qa_message 数据访问。
 */
@Mapper
public interface QaMessageMapper extends BaseMapper<QaMessage> {
}

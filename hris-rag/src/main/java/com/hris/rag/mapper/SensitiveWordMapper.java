package com.hris.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.rag.entity.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感词库 Mapper（KBD-F-017）。
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {
}

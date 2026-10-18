package com.hris.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.rag.entity.Document;
import org.apache.ibatis.annotations.Mapper;

/**
 * kb_document 数据访问。
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}

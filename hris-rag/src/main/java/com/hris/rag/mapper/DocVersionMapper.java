package com.hris.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.rag.entity.DocVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * kb_doc_version 数据访问。
 */
@Mapper
public interface DocVersionMapper extends BaseMapper<DocVersion> {
}

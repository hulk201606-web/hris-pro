package com.hris.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.rag.entity.Chunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * kb_chunk 数据访问。
 */
@Mapper
public interface ChunkMapper extends BaseMapper<Chunk> {
}

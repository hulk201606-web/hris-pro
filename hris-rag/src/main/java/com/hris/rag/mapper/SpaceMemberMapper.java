package com.hris.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.rag.entity.SpaceMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * kb_space_member 数据访问。
 */
@Mapper
public interface SpaceMemberMapper extends BaseMapper<SpaceMember> {
}

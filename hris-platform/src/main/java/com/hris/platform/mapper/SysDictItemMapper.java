package com.hris.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.platform.entity.SysDictItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典项数据访问。
 */
@Mapper
public interface SysDictItemMapper extends BaseMapper<SysDictItem> {
}

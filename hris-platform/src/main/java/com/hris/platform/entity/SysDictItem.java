package com.hris.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 字典项（广播表，docs/12 §5.10）。被业务数据引用时仅逻辑删除（见 DictItemService）。
 */
@TableName("sys_dict_item")
public class SysDictItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 字典类型编码（逻辑外键 sys_dict_type.code） */
    private String typeCode;

    /** 字典项标签 */
    private String label;

    /** 字典项值（同类型内唯一） */
    private String value;

    /** 排序 */
    private Integer sort;

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }
}

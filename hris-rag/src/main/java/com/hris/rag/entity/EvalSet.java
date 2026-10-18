package com.hris.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * kb_eval_set（由 DDL 自动生成）
 */
@TableName("kb_eval_set")
public class EvalSet extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private String name;  // 评测集名称

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    private String code;  // 评测集编码

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}

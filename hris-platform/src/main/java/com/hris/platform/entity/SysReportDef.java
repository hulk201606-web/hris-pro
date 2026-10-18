package com.hris.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 报表定义（PLT-F-005：报表目录，def_json 存报表查询定义）。
 */
@TableName("sys_report_def")
public class SysReportDef extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 报表名称 */
    private String name;

    /** 报表编码（租户内唯一） */
    private String code;

    /** 报表定义（JSON 字符串） */
    private String defJson;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDefJson() {
        return defJson;
    }

    public void setDefJson(String defJson) {
        this.defJson = defJson;
    }
}

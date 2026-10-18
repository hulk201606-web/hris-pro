package com.hris.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 法人主体（ORG-F-001）。
 */
@TableName("org_legal_entity")
public class OrgLegalEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 法人名称 */
    private String name;

    /** 统一社会信用代码（租户内唯一） */
    private String uscc;

    /** 记账本位币，默认 CNY */
    private String currency;

    /** 状态 1=有效 0=失效 */
    private Integer status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUscc() {
        return uscc;
    }

    public void setUscc(String uscc) {
        this.uscc = uscc;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

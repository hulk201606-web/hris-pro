package com.hris.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 租户参数（广播表，PLT-F-010：时区/日历/工号规则/邮件域名白名单）。
 */
@TableName("sys_tenant_config")
public class SysTenantConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }
}

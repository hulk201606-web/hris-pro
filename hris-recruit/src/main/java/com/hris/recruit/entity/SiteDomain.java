package com.hris.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * rcu_site_domain（批 7 #13）：官网租户域名映射，域名（全局唯一）命中自动定租户。
 */
@TableName("rcu_site_domain")
public class SiteDomain extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private String domain;  // 站点域名(小写,不含协议与端口)
    private String status;  // 状态 ENABLED/DISABLED

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

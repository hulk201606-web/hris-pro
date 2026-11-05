package com.hris.employee.client;

import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 平台配置中心 Feign 客户端（EMP-F-002 工号模板读取）。
 *
 * <p>sys_tenant_config 位于 plt_db，employee 不直连他域库，统一经 platform 内部端点
 * 读取；返回值缺失时由调用方回退缺省工号策略（fail-open，不阻断建档）。</p>
 */
@FeignClient(name = "hris-platform", path = "/internal/plt", contextId = "empPlatformConfigClient")
public interface PlatformConfigClient {

    /** 读取租户参数值；不存在时 data=null。 */
    @GetMapping("/tenant-configs")
    R<String> getTenantConfig(@RequestParam("key") String key);
}

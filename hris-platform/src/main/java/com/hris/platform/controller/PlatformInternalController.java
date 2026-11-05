package com.hris.platform.controller;

import com.hris.common.api.R;
import com.hris.platform.dto.InternalNotifyDtos.NotifyRequest;
import com.hris.platform.dto.InternalNotifyDtos.NotifyResponse;
import com.hris.platform.service.InternalNotifyService;
import com.hris.platform.service.TenantConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台内部端点（网关 InternalBanWebFilter 已封禁 /internal/** 公网访问）。
 *
 * <p>不挂 @RequirePerm：调用方为容器内服务（Feign + X-Tenant-Id），防伪造依赖网络边界；
 * 与既有 /internal/plt/audit 口径一致。</p>
 */
@RestController
@RequestMapping("/internal/plt")
public class PlatformInternalController {

    private final InternalNotifyService notifyService;
    private final TenantConfigService configService;

    public PlatformInternalController(InternalNotifyService notifyService,
                                      TenantConfigService configService) {
        this.notifyService = notifyService;
        this.configService = configService;
    }

    /**
     * 内部通知（RCS-F-018 Offer 发送）：三渠道路由，失败以 data.success=false + error 表达，
     * HTTP 始终 200（不把渠道故障升级为 5xx）。
     */
    @PostMapping("/notify")
    public R<NotifyResponse> notify(@RequestBody NotifyRequest req) {
        return R.ok(notifyService.notify(req));
    }

    /** 租户参数只读（EMP-F-002 工号模板等跨域配置），不存在时 data=null。 */
    @GetMapping("/tenant-configs")
    public R<String> tenantConfig(@RequestParam("key") String key) {
        return R.ok(configService.findValue(key));
    }
}

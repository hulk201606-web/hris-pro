package com.hris.platform.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.platform.dto.ConfigDtos.ConfigRequest;
import com.hris.platform.dto.ConfigDtos.ConfigResponse;
import com.hris.platform.service.TenantConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统配置中心端点（PLT-F-010）：租户参数按 key 读写。
 */
@RestController
@RequestMapping("/api/v1/plt/configs")
public class ConfigController {

    private final TenantConfigService configService;

    public ConfigController(TenantConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public R<List<ConfigResponse>> list() {
        return R.ok(configService.list());
    }

    @GetMapping("/{key}")
    public R<ConfigResponse> get(@PathVariable String key) {
        return R.ok(configService.get(key));
    }

    @RequirePerm("plt:config:write")
    @PutMapping("/{key}")
    public R<ConfigResponse> set(@PathVariable String key, @Valid @RequestBody ConfigRequest req) {
        return R.ok(configService.set(key, req));
    }

    @RequirePerm("plt:config:write")
    @DeleteMapping("/{key}")
    public R<Void> delete(@PathVariable String key) {
        configService.delete(key);
        return R.ok();
    }
}

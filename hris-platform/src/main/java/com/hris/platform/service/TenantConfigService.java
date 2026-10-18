package com.hris.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.ConfigDtos.ConfigRequest;
import com.hris.platform.dto.ConfigDtos.ConfigResponse;
import com.hris.platform.entity.SysTenantConfig;
import com.hris.platform.mapper.SysTenantConfigMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统配置中心（PLT-F-010：时区/日历/工号规则/邮件域名白名单，广播表）。按 config_key 幂等 upsert。
 */
@Service
public class TenantConfigService {

    private final SysTenantConfigMapper mapper;

    public TenantConfigService(SysTenantConfigMapper mapper) {
        this.mapper = mapper;
    }

    public List<ConfigResponse> list() {
        return mapper.selectList(new LambdaQueryWrapper<SysTenantConfig>()
                        .orderByAsc(SysTenantConfig::getConfigKey))
                .stream().map(this::toResponse).toList();
    }

    public ConfigResponse get(String key) {
        return toResponse(requireByKey(key));
    }

    /** 按 config_key upsert（存在则更新值，不存在则插入）。 */
    public ConfigResponse set(String key, ConfigRequest req) {
        String k = normalize(key);
        SysTenantConfig existing = findByKey(k);
        if (existing != null) {
            existing.setConfigValue(req.configValue());
            mapper.updateById(existing);
            return toResponse(existing);
        }
        SysTenantConfig e = new SysTenantConfig();
        e.setConfigKey(k);
        e.setConfigValue(req.configValue());
        mapper.insert(e);
        return toResponse(e);
    }

    public void delete(String key) {
        SysTenantConfig e = requireByKey(key);
        mapper.deleteById(e.getId());
    }

    private SysTenantConfig findByKey(String key) {
        return mapper.selectOne(new LambdaQueryWrapper<SysTenantConfig>()
                .eq(SysTenantConfig::getConfigKey, key));
    }

    private SysTenantConfig requireByKey(String key) {
        SysTenantConfig e = findByKey(normalize(key));
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "配置项不存在");
        }
        return e;
    }

    private String normalize(String key) {
        return key == null ? null : key.trim();
    }

    private ConfigResponse toResponse(SysTenantConfig e) {
        return new ConfigResponse(e.getId(), e.getConfigKey(), e.getConfigValue(), e.getUpdatedAt());
    }
}

package com.hris.compensation.service;

import com.hris.compensation.mapper.CmpTenantConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * 薪酬算薪参数读取（CMP-F-011 租户级开关）。
 *
 * <p>读取顺序（配置中心优先，部署默认兜底）：
 * <ol>
 *   <li>sys_tenant_config[config_key]（PLT 配置中心广播表，租户级覆盖，运行时生效）；</li>
 *   <li>Spring Environment 属性同名键（application.yml / 环境变量），缺省 {@code true}。</li>
 * </ol>
 * 配置中心不可用（表未同步/连接异常）时 fail-open 到部署默认值，绝不阻断算薪主流程。</p>
 */
@Service
public class CmpCalcConfigService {

    private static final Logger log = LoggerFactory.getLogger(CmpCalcConfigService.class);

    /** 离职当日计薪开关键（缺省 true：当日按 1 天计）。 */
    public static final String KEY_PAY_TERMINATION_DAY = "hris.cmp.calc.pay-termination-day";

    /** 缺省值：离职当日计薪。 */
    public static final boolean DEFAULT_PAY_TERMINATION_DAY = true;

    private final CmpTenantConfigMapper configMapper;
    private final Environment environment;

    public CmpCalcConfigService(CmpTenantConfigMapper configMapper, Environment environment) {
        this.configMapper = configMapper;
        this.environment = environment;
    }

    /**
     * 离职当日是否计薪：true=离职当日按 1 天计，false=离职当日不计。
     */
    public boolean payTerminationDay() {
        String raw = tenantValue();
        if (raw == null || raw.isBlank()) {
            raw = environment.getProperty(KEY_PAY_TERMINATION_DAY,
                    String.valueOf(DEFAULT_PAY_TERMINATION_DAY));
        }
        return parseBoolean(raw, DEFAULT_PAY_TERMINATION_DAY);
    }

    /** 租户配置读取（异常降级 null，交由 Environment 兜底）。 */
    private String tenantValue() {
        try {
            return configMapper.selectValue(KEY_PAY_TERMINATION_DAY);
        } catch (Exception e) {
            log.warn("租户配置读取失败，降级部署默认值 {}: {}", KEY_PAY_TERMINATION_DAY, e.getMessage());
            return null;
        }
    }

    /** 宽松布尔解析：false/0/no/off → false；其余 → 默认值（未知值不误判）。 */
    static boolean parseBoolean(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String v = raw.trim().toLowerCase();
        return switch (v) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> defaultValue;
        };
    }
}

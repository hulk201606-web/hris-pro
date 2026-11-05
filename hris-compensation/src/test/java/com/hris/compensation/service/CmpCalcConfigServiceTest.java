package com.hris.compensation.service;

import com.hris.compensation.mapper.CmpTenantConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 算薪开关读取单测（CMP-F-011）：配置中心优先、部署默认兜底、异常 fail-open。
 */
@ExtendWith(MockitoExtension.class)
class CmpCalcConfigServiceTest {

    @Mock
    private CmpTenantConfigMapper configMapper;
    @Mock
    private Environment environment;

    private CmpCalcConfigService service;

    @BeforeEach
    void setUp() {
        service = new CmpCalcConfigService(configMapper, environment);
    }

    @Test
    @DisplayName("租户配置 false → 离职当日不计薪（优先于环境默认）")
    void tenantFalseWins() {
        when(configMapper.selectValue(CmpCalcConfigService.KEY_PAY_TERMINATION_DAY)).thenReturn("false");

        assertFalse(service.payTerminationDay());
    }

    @Test
    @DisplayName("租户未配置 → 取部署默认 true")
    void fallsBackToDefault() {
        when(configMapper.selectValue(anyString())).thenReturn(null);
        when(environment.getProperty(anyString(), anyString())).thenReturn("true");

        assertTrue(service.payTerminationDay());
    }

    @Test
    @DisplayName("配置中心不可用 → 降级环境属性 false")
    void failsOpenToEnvironment() {
        when(configMapper.selectValue(anyString())).thenThrow(new RuntimeException("table missing"));
        when(environment.getProperty(anyString(), anyString())).thenReturn("false");

        assertFalse(service.payTerminationDay());
    }

    @Test
    @DisplayName("非法值 → 保持默认值 true，不误判")
    void unknownValueKeepsDefault() {
        when(configMapper.selectValue(anyString())).thenReturn("maybe");

        assertTrue(service.payTerminationDay());
    }
}

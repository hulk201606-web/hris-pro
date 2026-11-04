package com.hris.platform.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.ConfigDtos.ConfigRequest;
import com.hris.platform.entity.SysTenantConfig;
import com.hris.platform.mapper.SysTenantConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TenantConfigService 单元测试（PLT-F-010 系统配置中心）。
 *
 * <p>覆盖 config_key 归一化（trim）、按 key 幂等 upsert（存在更新/不存在插入）、
 * get/delete 的存在性守卫。</p>
 */
@ExtendWith(MockitoExtension.class)
class TenantConfigServiceTest {

    @Mock
    private SysTenantConfigMapper mapper;

    private TenantConfigService service;

    @BeforeEach
    void setUp() {
        service = new TenantConfigService(mapper);
    }

    private SysTenantConfig existing() {
        SysTenantConfig e = new SysTenantConfig();
        e.setId(1L);
        e.setConfigKey("timezone");
        e.setConfigValue("UTC");
        return e;
    }

    @Nested
    @DisplayName("get：按 key 查询")
    class Get {

        @Test
        @DisplayName("配置不存在 → NOT_FOUND")
        void notFound() {
            when(mapper.selectOne(any())).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.get("missing"));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("set：按 config_key 幂等 upsert")
    class Set {

        @Test
        @DisplayName("不存在 → 插入（key 去首尾空白）")
        void insertsWhenMissing() {
            when(mapper.selectOne(any())).thenReturn(null);

            service.set(" timezone ", new ConfigRequest("Asia/Shanghai"));

            ArgumentCaptor<SysTenantConfig> captor = ArgumentCaptor.forClass(SysTenantConfig.class);
            verify(mapper).insert(captor.capture());
            assertEquals("timezone", captor.getValue().getConfigKey(), "key 应去首尾空白");
            assertEquals("Asia/Shanghai", captor.getValue().getConfigValue());
        }

        @Test
        @DisplayName("存在 → 更新值，不重复插入")
        void updatesWhenExisting() {
            SysTenantConfig e = existing();
            when(mapper.selectOne(any())).thenReturn(e);

            service.set("timezone", new ConfigRequest("Asia/Shanghai"));

            verify(mapper).updateById(e);
            assertEquals("Asia/Shanghai", e.getConfigValue());
            verify(mapper, never()).insert(any(SysTenantConfig.class));
        }
    }

    @Nested
    @DisplayName("delete：存在性守卫")
    class Delete {

        @Test
        @DisplayName("配置不存在 → NOT_FOUND，不删除")
        void rejectsMissing() {
            when(mapper.selectOne(any())).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.delete("missing"));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(mapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("存在 → 按主键删除")
        void deletesExisting() {
            when(mapper.selectOne(any())).thenReturn(existing());

            service.delete("timezone");

            verify(mapper).deleteById(1L);
        }
    }
}

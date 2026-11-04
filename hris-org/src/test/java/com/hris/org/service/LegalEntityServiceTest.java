package com.hris.org.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.entity.OrgLegalEntity;
import com.hris.org.mapper.OrgLegalEntityMapper;
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
 * LegalEntityService 单元测试（ORG-F-001）。
 *
 * <p>法人主体与四个目录服务不同：无编码唯一性守卫（USCC 唯一性由 DB 约束兜底），
 * 业务逻辑集中在「USCC 大写归一化、币种/状态默认值、update 存在性守卫与局部更新」。</p>
 */
@ExtendWith(MockitoExtension.class)
class LegalEntityServiceTest {

    @Mock
    private OrgLegalEntityMapper mapper;

    private LegalEntityService service;

    @BeforeEach
    void setUp() {
        service = new LegalEntityService(mapper);
    }

    private OrgLegalEntity existing() {
        OrgLegalEntity e = new OrgLegalEntity();
        e.setId(7L);
        e.setName("上海总部");
        e.setUscc("91310000MA1FL0000X");
        e.setCurrency("CNY");
        e.setStatus(1);
        return e;
    }

    @Nested
    @DisplayName("create：字段归一化与默认值")
    class Create {

        @Test
        @DisplayName("USCC 转大写去空白；币种默认 CNY；status 未传默认 1；名称去首尾空白")
        void normalizesAndDefaults() {
            CatalogResponse resp = service.create(new CatalogRequest(" 上海总部 ", " 91310000ma1fl0000x ", null, null));

            ArgumentCaptor<OrgLegalEntity> captor = ArgumentCaptor.forClass(OrgLegalEntity.class);
            verify(mapper).insert(captor.capture());
            assertEquals("上海总部", captor.getValue().getName());
            assertEquals("91310000MA1FL0000X", captor.getValue().getUscc(), "USCC 应转大写并去空白");
            assertEquals("CNY", captor.getValue().getCurrency(), "币种未传应默认 CNY");
            assertEquals(1, captor.getValue().getStatus(), "status 未传应默认为 1");
            assertEquals("91310000MA1FL0000X", resp.code(), "响应 code 字段应映射为 USCC");
        }

        @Test
        @DisplayName("USCC 未传 → 保持 null 不 NPE")
        void nullUsccAllowed() {
            service.create(new CatalogRequest("上海总部", null, 0, null));

            ArgumentCaptor<OrgLegalEntity> captor = ArgumentCaptor.forClass(OrgLegalEntity.class);
            verify(mapper).insert(captor.capture());
            assertEquals(null, captor.getValue().getUscc());
            assertEquals(0, captor.getValue().getStatus(), "显式传 status=0 应保留");
        }
    }

    @Nested
    @DisplayName("update：存在性与局部更新")
    class Update {

        @Test
        @DisplayName("法人不存在 → NOT_FOUND，不更新")
        void notFound() {
            when(mapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.update(404L, new CatalogRequest("X", null, null, null)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(OrgLegalEntity.class));
        }

        @Test
        @DisplayName("名称为空白 → 不覆盖原名称；status 显式传入正常更新；USCC/币种不可变")
        void partialUpdate() {
            when(mapper.selectById(7L)).thenReturn(existing());

            service.update(7L, new CatalogRequest("   ", null, 0, null));

            ArgumentCaptor<OrgLegalEntity> captor = ArgumentCaptor.forClass(OrgLegalEntity.class);
            verify(mapper).updateById(captor.capture());
            assertEquals("上海总部", captor.getValue().getName(), "空白名称不应覆盖原名称");
            assertEquals(0, captor.getValue().getStatus());
            assertEquals("91310000MA1FL0000X", captor.getValue().getUscc(), "USCC 不可变更");
            assertEquals("CNY", captor.getValue().getCurrency(), "币种不可变更");
        }
    }
}

package com.hris.org.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.entity.OrgPosition;
import com.hris.org.mapper.OrgPositionMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PositionService 单元测试（ORG-F-006）。
 *
 * <p>结构同 GradeService：code 归一化 → 空则自动生成 pos 前缀 → ensureCodeAvailable 去重 → insert；
 * update/delete 先判存在性。覆盖编码守卫、默认值、局部更新与存在性守卫。</p>
 */
@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

    @Mock
    private OrgPositionMapper mapper;

    private PositionService service;

    @BeforeEach
    void setUp() {
        service = new PositionService(mapper);
    }

    private OrgPosition existing() {
        OrgPosition p = new OrgPosition();
        p.setId(7L);
        p.setName("高级工程师");
        p.setCode("pos-senior");
        p.setStatus(1);
        return p;
    }

    @Nested
    @DisplayName("编码守卫：同租户内岗位编码唯一")
    class Code {

        @Test
        @DisplayName("编码重复 → PARAM_INVALID，不落库")
        void rejectsDuplicateCode() {
            when(mapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class,
                    () -> service.create(new CatalogRequest("高级工程师", "pos-senior", null, null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).insert(any(OrgPosition.class));
        }

        @Test
        @DisplayName("编码为空白 → 自动生成 pos 前缀编码")
        void autoGeneratesCodeWhenBlank() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.create(new CatalogRequest("高级工程师", null, null, null));

            ArgumentCaptor<OrgPosition> captor = ArgumentCaptor.forClass(OrgPosition.class);
            verify(mapper).insert(captor.capture());
            assertTrue(captor.getValue().getCode().startsWith("pos"),
                    "空白编码应自动生成 pos 前缀，实际=" + captor.getValue().getCode());
        }

        @Test
        @DisplayName("编码归一化：' Pos-Senior ' 落库为 'pos-senior'")
        void normalizesCode() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.create(new CatalogRequest("高级工程师", " Pos-Senior ", null, null));

            ArgumentCaptor<OrgPosition> captor = ArgumentCaptor.forClass(OrgPosition.class);
            verify(mapper).insert(captor.capture());
            assertEquals("pos-senior", captor.getValue().getCode());
        }
    }

    @Nested
    @DisplayName("create：默认值与名称规范化")
    class Create {

        @Test
        @DisplayName("名称去首尾空白；status 未传时默认 1")
        void defaultsStatusAndTrimsName() {
            when(mapper.selectCount(any())).thenReturn(0L);

            CatalogResponse resp = service.create(new CatalogRequest("  高级工程师  ", "pos-senior", null, null));

            ArgumentCaptor<OrgPosition> captor = ArgumentCaptor.forClass(OrgPosition.class);
            verify(mapper).insert(captor.capture());
            assertEquals("高级工程师", captor.getValue().getName());
            assertEquals(1, captor.getValue().getStatus(), "status 未传应默认为 1");
            assertEquals(1, resp.status());
        }
    }

    @Nested
    @DisplayName("update：存在性与局部更新")
    class Update {

        @Test
        @DisplayName("岗位不存在 → NOT_FOUND，不更新")
        void notFound() {
            when(mapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.update(404L, new CatalogRequest("X", null, null, null)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(OrgPosition.class));
        }

        @Test
        @DisplayName("名称为空白 → 不覆盖原名称（局部更新语义）")
        void blankNameDoesNotOverwrite() {
            when(mapper.selectById(7L)).thenReturn(existing());

            service.update(7L, new CatalogRequest("   ", null, 0, null));

            ArgumentCaptor<OrgPosition> captor = ArgumentCaptor.forClass(OrgPosition.class);
            verify(mapper).updateById(captor.capture());
            assertEquals("高级工程师", captor.getValue().getName());
            assertEquals(0, captor.getValue().getStatus());
        }
    }

    @Nested
    @DisplayName("delete：存在性守卫")
    class Delete {

        @Test
        @DisplayName("岗位不存在 → NOT_FOUND，不删除")
        void rejectsMissing() {
            when(mapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.delete(404L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(mapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("存在 → 执行删除")
        void deletesExisting() {
            when(mapper.selectById(7L)).thenReturn(existing());

            service.delete(7L);

            verify(mapper).deleteById(7L);
        }
    }
}

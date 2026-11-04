package com.hris.org.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.entity.OrgGradeBand;
import com.hris.org.mapper.OrgGradeBandMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GradeBandService 单元测试（ORG-F-008 职级带）。
 *
 * <p>结构同 GradeService，但职级带无 status 字段：create 仅 name/code，update 仅名称可变更。
 * minGrade/maxGrade 带宽区间本增量不维护。覆盖编码守卫、默认前缀、局部更新与存在性守卫。</p>
 */
@ExtendWith(MockitoExtension.class)
class GradeBandServiceTest {

    @Mock
    private OrgGradeBandMapper mapper;

    private GradeBandService service;

    @BeforeEach
    void setUp() {
        service = new GradeBandService(mapper);
    }

    private OrgGradeBand existing() {
        OrgGradeBand b = new OrgGradeBand();
        b.setId(7L);
        b.setName("P4-P9");
        b.setCode("band-p4-p9");
        return b;
    }

    @Nested
    @DisplayName("编码守卫：同租户内职级带编码唯一")
    class Code {

        @Test
        @DisplayName("编码重复 → PARAM_INVALID，不落库")
        void rejectsDuplicateCode() {
            when(mapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class,
                    () -> service.create(new CatalogRequest("P4-P9", "band-p4-p9", null, null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).insert(any(OrgGradeBand.class));
        }

        @Test
        @DisplayName("编码为空白 → 自动生成 band 前缀编码")
        void autoGeneratesCodeWhenBlank() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.create(new CatalogRequest("P4-P9", null, null, null));

            ArgumentCaptor<OrgGradeBand> captor = ArgumentCaptor.forClass(OrgGradeBand.class);
            verify(mapper).insert(captor.capture());
            assertTrue(captor.getValue().getCode().startsWith("band"),
                    "空白编码应自动生成 band 前缀，实际=" + captor.getValue().getCode());
        }

        @Test
        @DisplayName("编码归一化：' Band-P4-P9 ' 落库为 'band-p4-p9'")
        void normalizesCode() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.create(new CatalogRequest("P4-P9", " Band-P4-P9 ", null, null));

            ArgumentCaptor<OrgGradeBand> captor = ArgumentCaptor.forClass(OrgGradeBand.class);
            verify(mapper).insert(captor.capture());
            assertEquals("band-p4-p9", captor.getValue().getCode());
        }
    }

    @Nested
    @DisplayName("create：名称规范化，无 status 字段")
    class Create {

        @Test
        @DisplayName("名称去首尾空白；响应 status 恒为 null（职级带无启停状态）")
        void trimsName() {
            when(mapper.selectCount(any())).thenReturn(0L);

            CatalogResponse resp = service.create(new CatalogRequest("  P4-P9  ", "band-p4-p9", 1, null));

            ArgumentCaptor<OrgGradeBand> captor = ArgumentCaptor.forClass(OrgGradeBand.class);
            verify(mapper).insert(captor.capture());
            assertEquals("P4-P9", captor.getValue().getName());
            assertNull(resp.status(), "职级带无 status，响应应恒为 null");
        }
    }

    @Nested
    @DisplayName("update：存在性与局部更新（仅名称可变更）")
    class Update {

        @Test
        @DisplayName("职级带不存在 → NOT_FOUND，不更新")
        void notFound() {
            when(mapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.update(404L, new CatalogRequest("X", null, null, null)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(OrgGradeBand.class));
        }

        @Test
        @DisplayName("名称为空白 → 不覆盖原名称")
        void blankNameDoesNotOverwrite() {
            when(mapper.selectById(7L)).thenReturn(existing());

            service.update(7L, new CatalogRequest("   ", null, null, null));

            ArgumentCaptor<OrgGradeBand> captor = ArgumentCaptor.forClass(OrgGradeBand.class);
            verify(mapper).updateById(captor.capture());
            assertEquals("P4-P9", captor.getValue().getName());
        }

        @Test
        @DisplayName("名称显式传入 → 正常更新；编码不可改")
        void updatesName() {
            when(mapper.selectById(7L)).thenReturn(existing());

            service.update(7L, new CatalogRequest("M1-M5", null, null, null));

            ArgumentCaptor<OrgGradeBand> captor = ArgumentCaptor.forClass(OrgGradeBand.class);
            verify(mapper).updateById(captor.capture());
            assertEquals("M1-M5", captor.getValue().getName());
            assertEquals("band-p4-p9", captor.getValue().getCode(), "编码不可变更");
        }
    }

    @Nested
    @DisplayName("delete：存在性守卫")
    class Delete {

        @Test
        @DisplayName("职级带不存在 → NOT_FOUND，不删除")
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

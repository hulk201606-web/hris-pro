package com.hris.org.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.entity.OrgGrade;
import com.hris.org.mapper.OrgGradeMapper;
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
 * GradeService 单元测试（ORG-F-008）——**岗位/职务/职级/职级带四个目录服务的代表**。
 *
 * <p>四者结构同构（code 归一化 → 空则自动生成 → ensureCodeAvailable 去重 → insert；
 * update/delete 先判存在性），故只对本类写全量守卫用例，其余三类复用同一套断言结构。
 * 选Grade 而非其余三者的原因：它额外承载 gradeBandId 分支，是四者中逻辑最厚的。</p>
 */
@ExtendWith(MockitoExtension.class)
class GradeServiceTest {

    @Mock
    private OrgGradeMapper mapper;

    private GradeService service;

    @BeforeEach
    void setUp() {
        service = new GradeService(mapper);
    }

    private OrgGrade existing() {
        OrgGrade g = new OrgGrade();
        g.setId(7L);
        g.setName("P6");
        g.setCode("p6");
        g.setGradeBandId(3L);
        g.setStatus(1);
        return g;
    }

    @Nested
    @DisplayName("编码守卫：同租户内职级编码唯一")
    class Code {

        @Test
        @DisplayName("编码重复 → PARAM_INVALID，不落库")
        void rejectsDuplicateCode() {
            when(mapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class,
                    () -> service.create(new CatalogRequest("P7", "p7", null, null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).insert(any(OrgGrade.class));
        }

        @Test
        @DisplayName("编码为空白 → 自动生成 grade 前缀编码")
        void autoGeneratesCodeWhenBlank() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.create(new CatalogRequest("P8", null, null, null));

            ArgumentCaptor<OrgGrade> captor = ArgumentCaptor.forClass(OrgGrade.class);
            verify(mapper).insert(captor.capture());
            assertTrue(captor.getValue().getCode().startsWith("grade"),
                    "空白编码应自动生成，实际=" + captor.getValue().getCode());
        }

        @Test
        @DisplayName("编码归一化：' P6 ' 落库为 'p6'")
        void normalizesCode() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.create(new CatalogRequest("P6", " P6 ", null, null));

            ArgumentCaptor<OrgGrade> captor = ArgumentCaptor.forClass(OrgGrade.class);
            verify(mapper).insert(captor.capture());
            assertEquals("p6", captor.getValue().getCode());
        }
    }

    @Nested
    @DisplayName("create：默认值与名称规范化")
    class Create {

        @Test
        @DisplayName("名称去首尾空白；status 未传时默认 1")
        void defaultsStatusAndTrimsName() {
            when(mapper.selectCount(any())).thenReturn(0L);

            CatalogResponse resp = service.create(new CatalogRequest("  P6  ", "p6", null, 3L));

            ArgumentCaptor<OrgGrade> captor = ArgumentCaptor.forClass(OrgGrade.class);
            verify(mapper).insert(captor.capture());
            assertEquals("P6", captor.getValue().getName());
            assertEquals(1, captor.getValue().getStatus(), "status 未传应默认为 1");
            assertEquals(3L, resp.gradeBandId());
        }
    }

    @Nested
    @DisplayName("update：存在性与局部更新")
    class Update {

        @Test
        @DisplayName("职级不存在 → NOT_FOUND，不更新")
        void notFound() {
            when(mapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.update(404L, new CatalogRequest("X", null, null, null)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(OrgGrade.class));
        }

        @Test
        @DisplayName("名称为空白 → 不覆盖原名称（局部更新语义）")
        void blankNameDoesNotOverwrite() {
            when(mapper.selectById(7L)).thenReturn(existing());

            service.update(7L, new CatalogRequest("   ", null, 0, null));

            ArgumentCaptor<OrgGrade> captor = ArgumentCaptor.forClass(OrgGrade.class);
            verify(mapper).updateById(captor.capture());
            assertEquals("P6", captor.getValue().getName());
            assertEquals(3L, captor.getValue().getGradeBandId(), "未传的字段应保持原值");
        }

        @Test
        @DisplayName("显式传入 gradeBandId 与 status → 正常更新")
        void updatesGradeBandAndStatus() {
            when(mapper.selectById(7L)).thenReturn(existing());

            service.update(7L, new CatalogRequest("P7", null, 0, 9L));

            ArgumentCaptor<OrgGrade> captor = ArgumentCaptor.forClass(OrgGrade.class);
            verify(mapper).updateById(captor.capture());
            assertEquals(9L, captor.getValue().getGradeBandId());
            assertEquals(0, captor.getValue().getStatus());
        }
    }

    @Nested
    @DisplayName("delete：存在性守卫")
    class Delete {

        @Test
        @DisplayName("职级不存在 → NOT_FOUND，不删除")
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
package com.hris.platform.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.DictDtos.DictTypeRequest;
import com.hris.platform.entity.SysDictType;
import com.hris.platform.mapper.SysDictTypeMapper;
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
 * DictTypeService 单元测试（PLT-F-007 字典类型管理）。
 *
 * <p>结构同 GradeService：code 归一化 → 空则自动生成 dict 前缀 → 去重 → insert；
 * update/delete 先判存在性。覆盖编码守卫、默认前缀、局部更新与存在性守卫。</p>
 */
@ExtendWith(MockitoExtension.class)
class DictTypeServiceTest {

    @Mock
    private SysDictTypeMapper mapper;

    private DictTypeService service;

    @BeforeEach
    void setUp() {
        service = new DictTypeService(mapper);
    }

    private SysDictType existing() {
        SysDictType e = new SysDictType();
        e.setId(7L);
        e.setName("婚姻状况");
        e.setCode("dict-marital");
        return e;
    }

    @Nested
    @DisplayName("编码守卫：同租户内字典类型编码唯一")
    class Code {

        @Test
        @DisplayName("编码重复 → PARAM_INVALID，不落库")
        void rejectsDuplicateCode() {
            when(mapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class,
                    () -> service.create(new DictTypeRequest("婚姻状况", "dict-marital")));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).insert(any(SysDictType.class));
        }

        @Test
        @DisplayName("编码为空白 → 自动生成 dict 前缀编码")
        void autoGeneratesCodeWhenBlank() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.create(new DictTypeRequest("婚姻状况", null));

            ArgumentCaptor<SysDictType> captor = ArgumentCaptor.forClass(SysDictType.class);
            verify(mapper).insert(captor.capture());
            assertTrue(captor.getValue().getCode().startsWith("dict"),
                    "空白编码应自动生成 dict 前缀，实际=" + captor.getValue().getCode());
        }

        @Test
        @DisplayName("编码归一化：' Dict-Marital ' 落库为 'dict-marital'")
        void normalizesCode() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.create(new DictTypeRequest("婚姻状况", " Dict-Marital "));

            ArgumentCaptor<SysDictType> captor = ArgumentCaptor.forClass(SysDictType.class);
            verify(mapper).insert(captor.capture());
            assertEquals("dict-marital", captor.getValue().getCode());
        }
    }

    @Nested
    @DisplayName("create：名称规范化")
    class Create {

        @Test
        @DisplayName("名称去首尾空白")
        void trimsName() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.create(new DictTypeRequest("  婚姻状况  ", "dict-marital"));

            ArgumentCaptor<SysDictType> captor = ArgumentCaptor.forClass(SysDictType.class);
            verify(mapper).insert(captor.capture());
            assertEquals("婚姻状况", captor.getValue().getName());
        }
    }

    @Nested
    @DisplayName("update：存在性与局部更新")
    class Update {

        @Test
        @DisplayName("字典类型不存在 → NOT_FOUND，不更新")
        void notFound() {
            when(mapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.update(404L, new DictTypeRequest("X", null)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(SysDictType.class));
        }

        @Test
        @DisplayName("名称为空白 → 不覆盖原名称")
        void blankNameDoesNotOverwrite() {
            when(mapper.selectById(7L)).thenReturn(existing());

            service.update(7L, new DictTypeRequest("   ", null));

            ArgumentCaptor<SysDictType> captor = ArgumentCaptor.forClass(SysDictType.class);
            verify(mapper).updateById(captor.capture());
            assertEquals("婚姻状况", captor.getValue().getName());
        }
    }

    @Nested
    @DisplayName("delete：存在性守卫")
    class Delete {

        @Test
        @DisplayName("字典类型不存在 → NOT_FOUND，不删除")
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

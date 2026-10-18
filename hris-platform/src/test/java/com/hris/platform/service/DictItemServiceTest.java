package com.hris.platform.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.DictDtos.DictItemRequest;
import com.hris.platform.entity.SysDictItem;
import com.hris.platform.mapper.SysDictItemMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DictItemService 单元测试（PLT-F-007 字典项）。
 *
 * <p>值得测的核心是<b>value 为空白时回退用 label</b>这条业务默认（前端只传 label 也能建项），
 * 以及类型存在性与同类 value 唯一两道数据完整性守卫。
 * 注：跨服务引用探测 probeReferenced 为 MVP 恒false 扩展点，属未实现功能，不做断言。</p>
 */
@ExtendWith(MockitoExtension.class)
class DictItemServiceTest {

    @Mock
    private SysDictItemMapper itemMapper;
    @Mock
    private SysDictTypeMapper typeMapper;

    private DictItemService service;

    @BeforeEach
    void setUp() {
        service = new DictItemService(itemMapper, typeMapper);
    }

    @Nested
    @DisplayName("create：value 回退默认值")
    class ValueFallback {

        @Test
        @DisplayName("value 为空 → 回退使用 label 作为 value（前端只传标签也能建项）")
        void fallsBackToLabelWhenValueBlank() {
            when(typeMapper.selectCount(any())).thenReturn(1L);
            when(itemMapper.selectCount(any())).thenReturn(0L);

            service.create("gender", new DictItemRequest("男", null, 1));

            ArgumentCaptor<SysDictItem> captor = ArgumentCaptor.forClass(SysDictItem.class);
            verify(itemMapper).insert(captor.capture());
            assertEquals("男", captor.getValue().getValue(), "value 空白时应回退为 label");
        }

        @Test
        @DisplayName("value 为纯空白 → 同样回退为 label")
        void fallsBackWhenValueIsWhitespace() {
            when(typeMapper.selectCount(any())).thenReturn(1L);
            when(itemMapper.selectCount(any())).thenReturn(0L);

            service.create("gender", new DictItemRequest("女", "   ", 1));

            ArgumentCaptor<SysDictItem> captor = ArgumentCaptor.forClass(SysDictItem.class);
            verify(itemMapper).insert(captor.capture());
            assertEquals("女", captor.getValue().getValue());
        }

        @Test
        @DisplayName("value 显式传入 → 优先使用 value，不被 label 覆盖")
        void prefersExplicitValue() {
            when(typeMapper.selectCount(any())).thenReturn(1L);
            when(itemMapper.selectCount(any())).thenReturn(0L);

            service.create("gender", new DictItemRequest("男", "MALE", 1));

            ArgumentCaptor<SysDictItem> captor = ArgumentCaptor.forClass(SysDictItem.class);
            verify(itemMapper).insert(captor.capture());
            assertEquals("MALE", captor.getValue().getValue());
        }
    }

    @Nested
    @DisplayName("create：类型存在性与唯一性守卫")
    class Guards {

        @Test
        @DisplayName("字典类型不存在 → NOT_FOUND，不落库（防孤儿字典项）")
        void rejectsUnknownType() {
            when(typeMapper.selectCount(any())).thenReturn(0L);

            BizException e = assertThrows(BizException.class,
                    () -> service.create("not_exist", new DictItemRequest("标签", "V", 1)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(itemMapper, never()).insert(any(SysDictItem.class));
        }

        @Test
        @DisplayName("同类型下 value 重复 → PARAM_INVALID，不落库")
        void rejectsDuplicateValue() {
            when(typeMapper.selectCount(any())).thenReturn(1L);
            when(itemMapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class,
                    () -> service.create("gender", new DictItemRequest("男", "MALE", 1)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(itemMapper, never()).insert(any(SysDictItem.class));
        }

        @Test
        @DisplayName("sort 未传 → 落库为 0")
        void defaultsSortToZero() {
            when(typeMapper.selectCount(any())).thenReturn(1L);
            when(itemMapper.selectCount(any())).thenReturn(0L);

            service.create("gender", new DictItemRequest("男", "MALE", null));

            ArgumentCaptor<SysDictItem> captor = ArgumentCaptor.forClass(SysDictItem.class);
            verify(itemMapper).insert(captor.capture());
            assertEquals(0, captor.getValue().getSort());
        }

        @Test
        @DisplayName("typeCode 去首尾空白 → 落库为trim 后的值")
        void trimsTypeCode() {
            when(typeMapper.selectCount(any())).thenReturn(1L);
            when(itemMapper.selectCount(any())).thenReturn(0L);

            service.create("  gender  ", new DictItemRequest("男", "MALE", 1));

            ArgumentCaptor<SysDictItem> captor = ArgumentCaptor.forClass(SysDictItem.class);
            verify(itemMapper).insert(captor.capture());
            assertEquals("gender", captor.getValue().getTypeCode());
        }
    }

    @Nested
    @DisplayName("update / delete：存在性守卫")
    class Existence {

        @Test
        @DisplayName("更新不存在的字典项 → NOT_FOUND")
        void updateNotFound() {
            when(itemMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.update(404L, new DictItemRequest("新标签", "V", 1)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(itemMapper, never()).updateById(any(SysDictItem.class));
        }

        @Test
        @DisplayName("删除不存在的字典项 → NOT_FOUND，不执行删除")
        void deleteNotFound() {
            when(itemMapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.delete(404L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(itemMapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("更新时 label 为空白 → 不覆盖原值（局部更新语义）")
        void blankLabelDoesNotOverwrite() {
            SysDictItem item = new SysDictItem();
            item.setId(1L);
            item.setTypeCode("gender");
            item.setLabel("男");
            item.setValue("MALE");
            item.setSort(1);
            when(itemMapper.selectById(1L)).thenReturn(item);

            service.update(1L, new DictItemRequest("   ", "FEMALE", null));

            ArgumentCaptor<SysDictItem> captor = ArgumentCaptor.forClass(SysDictItem.class);
            verify(itemMapper).updateById(captor.capture());
            assertEquals("男", captor.getValue().getLabel(), "空白 label 不应覆盖原值");
            assertEquals(1, captor.getValue().getSort(), "未传的sort 应保持原值");
        }
    }
}
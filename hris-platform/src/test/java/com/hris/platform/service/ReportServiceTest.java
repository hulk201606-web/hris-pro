package com.hris.platform.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.ReportDtos.ReportDefRequest;
import com.hris.platform.entity.SysReportDef;
import com.hris.platform.mapper.SysReportDefMapper;
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
 * ReportService 单元测试（PLT-F-005 报表定义管理）。
 *
 * <p>结构同 GradeService：code 归一化 → 空则自动生成 rpt 前缀 → 去重 → insert；
 * update/delete 先判存在性。覆盖编码守卫、默认前缀、局部更新与存在性守卫。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private SysReportDefMapper mapper;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(mapper);
    }

    private SysReportDef existing() {
        SysReportDef e = new SysReportDef();
        e.setId(7L);
        e.setName("在职花名册");
        e.setCode("rpt-roster");
        e.setDefJson("{\"query\":\"select *\"}");
        return e;
    }

    @Nested
    @DisplayName("编码守卫：同租户内报表编码唯一")
    class Code {

        @Test
        @DisplayName("编码重复 → PARAM_INVALID，不落库")
        void rejectsDuplicateCode() {
            when(mapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class,
                    () -> service.create(new ReportDefRequest("在职花名册", "rpt-roster", null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).insert(any(SysReportDef.class));
        }

        @Test
        @DisplayName("编码为空白 → 自动生成 rpt 前缀编码")
        void autoGeneratesCodeWhenBlank() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.create(new ReportDefRequest("在职花名册", null, null));

            ArgumentCaptor<SysReportDef> captor = ArgumentCaptor.forClass(SysReportDef.class);
            verify(mapper).insert(captor.capture());
            assertTrue(captor.getValue().getCode().startsWith("rpt"),
                    "空白编码应自动生成 rpt 前缀，实际=" + captor.getValue().getCode());
        }

        @Test
        @DisplayName("编码归一化：' Rpt-Roster ' 落库为 'rpt-roster'")
        void normalizesCode() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.create(new ReportDefRequest("在职花名册", " Rpt-Roster ", null));

            ArgumentCaptor<SysReportDef> captor = ArgumentCaptor.forClass(SysReportDef.class);
            verify(mapper).insert(captor.capture());
            assertEquals("rpt-roster", captor.getValue().getCode());
        }
    }

    @Nested
    @DisplayName("create：名称规范化")
    class Create {

        @Test
        @DisplayName("名称去首尾空白；defJson 原样保存")
        void trimsNameAndSavesDef() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.create(new ReportDefRequest("  在职花名册  ", "rpt-roster", "{\"query\":\"x\"}"));

            ArgumentCaptor<SysReportDef> captor = ArgumentCaptor.forClass(SysReportDef.class);
            verify(mapper).insert(captor.capture());
            assertEquals("在职花名册", captor.getValue().getName());
            assertEquals("{\"query\":\"x\"}", captor.getValue().getDefJson());
        }
    }

    @Nested
    @DisplayName("update：存在性与局部更新")
    class Update {

        @Test
        @DisplayName("报表不存在 → NOT_FOUND，不更新")
        void notFound() {
            when(mapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.update(404L, new ReportDefRequest("X", null, null)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(SysReportDef.class));
        }

        @Test
        @DisplayName("名称为空白 → 不覆盖原名称；defJson 显式传入才更新")
        void partialUpdate() {
            when(mapper.selectById(7L)).thenReturn(existing());

            service.update(7L, new ReportDefRequest("   ", null, "{\"query\":\"new\"}"));

            ArgumentCaptor<SysReportDef> captor = ArgumentCaptor.forClass(SysReportDef.class);
            verify(mapper).updateById(captor.capture());
            assertEquals("在职花名册", captor.getValue().getName(), "空白名称不应覆盖原名称");
            assertEquals("{\"query\":\"new\"}", captor.getValue().getDefJson());
        }
    }

    @Nested
    @DisplayName("delete：存在性守卫")
    class Delete {

        @Test
        @DisplayName("报表不存在 → NOT_FOUND，不删除")
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

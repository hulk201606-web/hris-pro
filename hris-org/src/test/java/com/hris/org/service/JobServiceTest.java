package com.hris.org.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.entity.OrgJob;
import com.hris.org.mapper.OrgJobMapper;
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
 * JobService 单元测试（ORG-F-007）。
 *
 * <p>结构同 GradeService：code 归一化 → 空则自动生成 job 前缀 → ensureCodeAvailable 去重 → insert；
 * update/delete 先判存在性。覆盖编码守卫、默认值、局部更新与存在性守卫。</p>
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private OrgJobMapper mapper;

    private JobService service;

    @BeforeEach
    void setUp() {
        service = new JobService(mapper);
    }

    private OrgJob existing() {
        OrgJob j = new OrgJob();
        j.setId(7L);
        j.setName("技术经理");
        j.setCode("job-tech-lead");
        j.setStatus(1);
        return j;
    }

    @Nested
    @DisplayName("编码守卫：同租户内职务编码唯一")
    class Code {

        @Test
        @DisplayName("编码重复 → PARAM_INVALID，不落库")
        void rejectsDuplicateCode() {
            when(mapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class,
                    () -> service.create(new CatalogRequest("技术经理", "job-tech-lead", null, null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).insert(any(OrgJob.class));
        }

        @Test
        @DisplayName("编码为空白 → 自动生成 job 前缀编码")
        void autoGeneratesCodeWhenBlank() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.create(new CatalogRequest("技术经理", null, null, null));

            ArgumentCaptor<OrgJob> captor = ArgumentCaptor.forClass(OrgJob.class);
            verify(mapper).insert(captor.capture());
            assertTrue(captor.getValue().getCode().startsWith("job"),
                    "空白编码应自动生成 job 前缀，实际=" + captor.getValue().getCode());
        }

        @Test
        @DisplayName("编码归一化：' Job-Tech-Lead ' 落库为 'job-tech-lead'")
        void normalizesCode() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.create(new CatalogRequest("技术经理", " Job-Tech-Lead ", null, null));

            ArgumentCaptor<OrgJob> captor = ArgumentCaptor.forClass(OrgJob.class);
            verify(mapper).insert(captor.capture());
            assertEquals("job-tech-lead", captor.getValue().getCode());
        }
    }

    @Nested
    @DisplayName("create：默认值与名称规范化")
    class Create {

        @Test
        @DisplayName("名称去首尾空白；status 未传时默认 1")
        void defaultsStatusAndTrimsName() {
            when(mapper.selectCount(any())).thenReturn(0L);

            CatalogResponse resp = service.create(new CatalogRequest("  技术经理  ", "job-tech-lead", null, null));

            ArgumentCaptor<OrgJob> captor = ArgumentCaptor.forClass(OrgJob.class);
            verify(mapper).insert(captor.capture());
            assertEquals("技术经理", captor.getValue().getName());
            assertEquals(1, captor.getValue().getStatus(), "status 未传应默认为 1");
            assertEquals(1, resp.status());
        }
    }

    @Nested
    @DisplayName("update：存在性与局部更新")
    class Update {

        @Test
        @DisplayName("职务不存在 → NOT_FOUND，不更新")
        void notFound() {
            when(mapper.selectById(404L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.update(404L, new CatalogRequest("X", null, null, null)));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(OrgJob.class));
        }

        @Test
        @DisplayName("名称为空白 → 不覆盖原名称；编码作为身份标识不可改")
        void blankNameDoesNotOverwrite() {
            when(mapper.selectById(7L)).thenReturn(existing());

            service.update(7L, new CatalogRequest("   ", null, 0, null));

            ArgumentCaptor<OrgJob> captor = ArgumentCaptor.forClass(OrgJob.class);
            verify(mapper).updateById(captor.capture());
            assertEquals("技术经理", captor.getValue().getName());
            assertEquals("job-tech-lead", captor.getValue().getCode(), "编码不可变更");
            assertEquals(0, captor.getValue().getStatus());
        }
    }

    @Nested
    @DisplayName("delete：存在性守卫")
    class Delete {

        @Test
        @DisplayName("职务不存在 → NOT_FOUND，不删除")
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

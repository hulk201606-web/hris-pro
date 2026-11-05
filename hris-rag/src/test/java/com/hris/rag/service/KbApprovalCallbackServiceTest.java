package com.hris.rag.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.rag.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.rag.entity.DocVersion;
import com.hris.rag.mapper.DocVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KbApprovalCallbackService 单元测试（docs/19 §9 遗留项四域接入）：
 * KBD_PUBLISH 状态迁移幂等（PENDING → PUBLISHED）。
 */
@ExtendWith(MockitoExtension.class)
class KbApprovalCallbackServiceTest {

    @Mock
    private DocVersionMapper docVersionMapper;

    private KbApprovalCallbackService service;

    @BeforeEach
    void setUp() {
        service = new KbApprovalCallbackService(docVersionMapper);
    }

    private DocVersion version(String status) {
        DocVersion e = new DocVersion();
        e.setId(3001L);
        e.setStatus(status);
        return e;
    }

    @Nested
    @DisplayName("KBD_PUBLISH：PENDING → PUBLISHED，幂等")
    class PublishCallback {

        @Test
        @DisplayName("PENDING + approve → PUBLISHED")
        void approvesToPublished() {
            when(docVersionMapper.selectById(3001L)).thenReturn(version("PENDING"));

            ApprovalCallbackResponse resp = service.handleCallback("KBD_PUBLISH", "3001", true);

            assertTrue(resp.changed());
            assertEquals("PUBLISHED", resp.status());
            verify(docVersionMapper).updateById(any(DocVersion.class));
        }

        @Test
        @DisplayName("PENDING + reject → 无载体维持 PENDING，changed=false")
        void rejectKeepsPending() {
            when(docVersionMapper.selectById(3001L)).thenReturn(version("PENDING"));

            ApprovalCallbackResponse resp = service.handleCallback("KBD_PUBLISH", "3001", false);

            assertFalse(resp.changed());
            assertEquals("PENDING", resp.status());
            verify(docVersionMapper, never()).updateById(any(DocVersion.class));
        }

        @Test
        @DisplayName("已 PUBLISHED（终态重复回调）→ changed=false，不重复变更")
        void idempotentOnPublished() {
            when(docVersionMapper.selectById(3001L)).thenReturn(version("PUBLISHED"));

            ApprovalCallbackResponse resp = service.handleCallback("KBD_PUBLISH", "3001", true);

            assertFalse(resp.changed());
            assertEquals("PUBLISHED", resp.status());
            verify(docVersionMapper, never()).updateById(any(DocVersion.class));
        }

        @Test
        @DisplayName("已 ARCHIVED/EXPIRED（终态重复回调）→ changed=false")
        void idempotentOnArchived() {
            when(docVersionMapper.selectById(3001L)).thenReturn(version("ARCHIVED"));

            ApprovalCallbackResponse resp = service.handleCallback("KBD_PUBLISH", "3001", true);

            assertFalse(resp.changed());
            verify(docVersionMapper, never()).updateById(any(DocVersion.class));
        }

        @Test
        @DisplayName("DRAFT（流程未提交）→ changed=false")
        void skipsDraft() {
            when(docVersionMapper.selectById(3001L)).thenReturn(version("DRAFT"));

            ApprovalCallbackResponse resp = service.handleCallback("KBD_PUBLISH", "3001", true);

            assertFalse(resp.changed());
            verify(docVersionMapper, never()).updateById(any(DocVersion.class));
        }

        @Test
        @DisplayName("版本不存在 → NOT_FOUND")
        void notFound() {
            when(docVersionMapper.selectById(3001L)).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.handleCallback("KBD_PUBLISH", "3001", true));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("bizKey 非数字 → PARAM_INVALID")
        void invalidBizKey() {
            BizException e = assertThrows(BizException.class,
                    () -> service.handleCallback("KBD_PUBLISH", "DOC-V1", true));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }
    }

    @Test
    @DisplayName("未知 bizType → changed=false，不抛异常")
    void unknownBizType() {
        ApprovalCallbackResponse resp = service.handleCallback("KBD_UNKNOWN", "1", true);

        assertFalse(resp.changed());
        verify(docVersionMapper, never()).selectById(any());
    }
}

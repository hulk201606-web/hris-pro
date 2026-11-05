package com.hris.rag.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.rag.dto.DocVersionDtos.DocVersionCreateRequest;
import com.hris.rag.dto.DocVersionDtos.DocVersionResponse;
import com.hris.rag.dto.DocVersionDtos.DocVersionUpdateRequest;
import com.hris.rag.entity.DocVersion;
import com.hris.rag.entity.Document;
import com.hris.rag.mapper.DocVersionMapper;
import com.hris.rag.mapper.DocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DocVersionService 单元测试（docs/19 §9 遗留项：KBD_PUBLISH 审批发起接入）：
 * submit 状态守卫（仅 DRAFT 可提交）、fail-open、bizKey = 版本 id 十进制串约定。
 */
@ExtendWith(MockitoExtension.class)
class DocVersionServiceTest {

    private static final Long VERSION_ID = 3001L;

    @Mock
    private DocVersionMapper mapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private FlowIntegration flowIntegration;

    private DocVersionService service;

    @BeforeEach
    void setUp() {
        service = new DocVersionService(mapper, documentMapper, flowIntegration);
    }

    private DocVersion version(String status) {
        DocVersion e = new DocVersion();
        e.setId(VERSION_ID);
        e.setDocumentId(900L);
        e.setStatus(status);
        return e;
    }

    private Document document() {
        Document d = new Document();
        d.setId(900L);
        d.setSpaceId(7L);
        d.setTitle("员工手册 v2");
        return d;
    }

    @Nested
    @DisplayName("提交发布：DRAFT → PENDING + 发起 KBD_PUBLISH")
    class Submit {

        @Test
        @DisplayName("DRAFT 提交 → PENDING，流程 bizKey = 版本 id 十进制串，payload 含文档名/空间摘要")
        void draftSubmitsToPending() {
            when(mapper.selectById(VERSION_ID)).thenReturn(version("DRAFT"));
            when(documentMapper.selectById(900L)).thenReturn(document());
            when(flowIntegration.start(anyString(), anyString(), any())).thenReturn("pi-77");
            when(mapper.updateById(any(DocVersion.class))).thenReturn(1);

            DocVersionResponse resp = service.submit(VERSION_ID);

            assertEquals("PENDING", resp.status());
            verify(flowIntegration).start(eq(DocVersionService.BIZ_TYPE_PUBLISH), eq("3001"), any());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(flowIntegration).start(anyString(), anyString(), captor.capture());
            Map<String, Object> payload = captor.getValue();
            assertEquals(900L, payload.get("documentId"));
            assertEquals(7L, payload.get("spaceId"));
            assertEquals("员工手册 v2", payload.get("docTitleSummary"));
        }

        @Test
        @DisplayName("PENDING 重复提交 → 拒绝，不重发流程")
        void pendingRejectsResubmit() {
            when(mapper.selectById(VERSION_ID)).thenReturn(version("PENDING"));

            BizException e = assertThrows(BizException.class, () -> service.submit(VERSION_ID));

            assertEquals(ErrorCode.RAG_ERROR.getCode(), e.getCode());
            verify(flowIntegration, never()).start(anyString(), anyString(), any());
            verify(mapper, never()).updateById(any(DocVersion.class));
        }

        @Test
        @DisplayName("终态 PUBLISHED/EXPIRED/ARCHIVED 提交 → 拒绝")
        void terminalStatesReject() {
            for (String terminal : new String[]{"PUBLISHED", "EXPIRED", "ARCHIVED"}) {
                when(mapper.selectById(VERSION_ID)).thenReturn(version(terminal));
                assertThrows(BizException.class, () -> service.submit(VERSION_ID), terminal);
            }
            verify(flowIntegration, never()).start(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("版本不存在 → NOT_FOUND")
        void notFound() {
            when(mapper.selectById(VERSION_ID)).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.submit(VERSION_ID));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("fail-open 与旁路清理")
    class GuardAndFailOpen {

        @Test
        @DisplayName("flow 不可用（返回 null）→ 版本保留 PENDING，不阻断提交")
        void failOpenKeepsPending() {
            when(mapper.selectById(VERSION_ID)).thenReturn(version("DRAFT"));
            when(documentMapper.selectById(900L)).thenReturn(document());
            when(flowIntegration.start(anyString(), anyString(), any())).thenReturn(null);
            when(mapper.updateById(any(DocVersion.class))).thenReturn(1);

            DocVersionResponse resp = service.submit(VERSION_ID);

            assertEquals("PENDING", resp.status());
            verify(mapper).updateById(any(DocVersion.class));
        }

        @Test
        @DisplayName("创建恒置 DRAFT，status 不可由请求指定")
        void createForcesDraft() {
            when(mapper.insert(any(DocVersion.class))).thenReturn(1);

            DocVersionResponse resp = service.create(new DocVersionCreateRequest(900L, null, null, null));

            assertEquals("DRAFT", resp.status());
        }

        @Test
        @DisplayName("更新不改写状态：直填通道已移除，status 保持原值")
        void updateKeepsStatus() {
            when(mapper.selectById(VERSION_ID)).thenReturn(version("PENDING"));
            when(mapper.updateById(any(DocVersion.class))).thenReturn(1);

            DocVersionResponse resp = service.update(VERSION_ID, new DocVersionUpdateRequest(900L, null, null, null));

            assertEquals("PENDING", resp.status());
            verify(flowIntegration, never()).start(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("bizKey 格式：版本 id 十进制字符串（回调按主键定位的约定）")
        void bizKeyIsDecimalString() {
            when(mapper.selectById(VERSION_ID)).thenReturn(version("DRAFT"));
            when(documentMapper.selectById(900L)).thenReturn(document());
            when(flowIntegration.start(anyString(), anyString(), any())).thenReturn("pi-1");
            when(mapper.updateById(any(DocVersion.class))).thenReturn(1);

            service.submit(VERSION_ID);

            verify(flowIntegration).start(eq(DocVersionService.BIZ_TYPE_PUBLISH), eq(String.valueOf(VERSION_ID)), any());
            assertTrue(String.valueOf(VERSION_ID).matches("\\d+"));
        }
    }
}

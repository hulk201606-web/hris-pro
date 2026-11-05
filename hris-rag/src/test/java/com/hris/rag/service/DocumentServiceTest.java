package com.hris.rag.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.rag.dto.DocumentDtos.DocumentCreateRequest;
import com.hris.rag.dto.DocumentDtos.DocumentResponse;
import com.hris.rag.entity.Document;
import com.hris.rag.mapper.DocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DocumentService 敏感词接入测试（KBD-F-017）：BLOCK 拒绝落库 / WARN 附加 warnings / 无词放行。
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentMapper mapper;
    @Mock
    private SensitiveWordService sensitiveWordService;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(mapper, sensitiveWordService);
    }

    private DocumentCreateRequest req(String title) {
        return new DocumentCreateRequest(1L, title, "H-" + title, "DOC", null);
    }

    @Test
    @DisplayName("BLOCK 命中 → 拒绝，不落库")
    void blocksDocument() {
        when(sensitiveWordService.scan("赌博操作手册"))
                .thenThrow(new BizException(ErrorCode.PARAM_INVALID, "内容包含敏感词：赌博"));

        BizException e = assertThrows(BizException.class, () -> service.create(req("赌博操作手册")));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        verify(mapper, never()).insert(any(Document.class));
    }

    @Test
    @DisplayName("WARN 命中 → 正常落库，响应附带 warnings")
    void warnsAndPersists() {
        when(sensitiveWordService.scan("内部未公开制度")).thenReturn(List.of("内部未公开"));
        when(mapper.insert(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });

        DocumentResponse resp = service.create(req("内部未公开制度"));

        assertEquals(List.of("内部未公开"), resp.warnings());
        verify(mapper).insert(any(Document.class));
    }

    @Test
    @DisplayName("无词命中 → 放行，warnings 为空")
    void passesCleanDocument() {
        when(sensitiveWordService.scan("员工手册")).thenReturn(List.of());
        when(mapper.insert(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });

        DocumentResponse resp = service.create(req("员工手册"));

        assertTrue(resp.warnings().isEmpty());
    }
}

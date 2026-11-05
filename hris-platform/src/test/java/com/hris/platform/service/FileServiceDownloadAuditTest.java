package com.hris.platform.service;

import com.hris.platform.dto.FileDtos.FileDownloadResponse;
import com.hris.platform.entity.SysFileMeta;
import com.hris.platform.mapper.SysFileMetaMapper;
import com.hris.platform.storage.MinioStorage;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FileService.download 审计与水印测试（PLT-F-002 深化）。
 *
 * <p>守卫：下载必落审计（失败不影响下载）；watermarkPolicy 开启时
 * presign 附带 response-content-disposition（文件名带「[水印]」前缀）且响应带 watermark=true。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileServiceDownloadAuditTest {

    @Mock
    private SysFileMetaMapper fileMetaMapper;
    @Mock
    private MinioStorage minioStorage;
    @Mock
    private AuditLogService auditLogService;

    private FileService service;

    @BeforeEach
    void setUp() {
        service = new FileService(fileMetaMapper, minioStorage, new com.hris.platform.config.MinioProperties(),
                auditLogService);
    }

    private SysFileMeta meta(String watermarkPolicy) {
        SysFileMeta m = new SysFileMeta();
        m.setId(5L);
        m.setBucket("hris-files");
        m.setObjectKey("tenant-1/hr/202601/u-a.pdf");
        m.setFileName("合同.pdf");
        m.setWatermarkPolicy(watermarkPolicy);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedAuditDetail() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).append(eq("file:download"), eq("file/5"), captor.capture(), any());
        return captor.getValue();
    }

    @Nested
    @DisplayName("下载审计")
    class Audit {

        @Test
        @DisplayName("下载成功 → 落审计行：fileId/objectKey/watermarkPolicy 齐全")
        void writesAuditOnDownload() {
            when(fileMetaMapper.selectById(5L)).thenReturn(meta(null));
            when(minioStorage.presignGet(anyString(), anyString(), anyInt())).thenReturn("http://minio/get");

            service.download(5L);

            Map<String, Object> detail = capturedAuditDetail();
            assertEquals(5L, detail.get("fileId"));
            assertEquals("tenant-1/hr/202601/u-a.pdf", detail.get("objectKey"));
            assertEquals(false, detail.get("watermark"));
        }

        @Test
        @DisplayName("审计写入抛异常 → 下载不受影响（try/catch 收敛）")
        void auditFailureDoesNotBreakDownload() {
            when(fileMetaMapper.selectById(5L)).thenReturn(meta(null));
            when(minioStorage.presignGet(anyString(), anyString(), anyInt())).thenReturn("http://minio/get");
            doThrow(new RuntimeException("audit down")).when(auditLogService)
                    .append(anyString(), anyString(), any(), any());

            FileDownloadResponse resp = service.download(5L);

            assertEquals("http://minio/get", resp.downloadUrl());
            assertFalse(resp.degraded());
        }
    }

    @Nested
    @DisplayName("水印策略分支")
    class Watermark {

        @Test
        @DisplayName("policy=ON → presign 带 content-disposition（[水印] 前缀），响应 watermark=true")
        void watermarkOnAddsDisposition() {
            SysFileMeta m = meta("ON");
            m.setFileName("合同\".pdf");
            when(fileMetaMapper.selectById(5L)).thenReturn(m);
            when(minioStorage.presignGet(anyString(), anyString(), anyInt(), any()))
                    .thenReturn("http://minio/get?disposition=signed");

            FileDownloadResponse resp = service.download(5L);

            assertTrue(resp.watermark());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> extra = ArgumentCaptor.forClass(Map.class);
            verify(minioStorage).presignGet(eq("hris-files"), eq("tenant-1/hr/202601/u-a.pdf"),
                    anyInt(), extra.capture());
            String cd = extra.getValue().get("response-content-disposition");
            assertTrue(cd != null && cd.contains("[水印]"), "实际=" + cd);
            // 文件名消毒：引号被替换为下划线，防 content-disposition 头注入
            assertTrue(cd.contains("合同_.pdf"), "实际=" + cd);
            assertFalse(cd.contains("\".pdf"), "消毒后不应残留原始引号，实际=" + cd);
            Map<String, Object> detail = capturedAuditDetail();
            assertEquals(true, detail.get("watermark"));
        }

        @Test
        @DisplayName("policy 未开启 → 不带 disposition 参数，watermark=false")
        void watermarkOffPlainPresign() {
            when(fileMetaMapper.selectById(5L)).thenReturn(meta("OFF"));
            when(minioStorage.presignGet(anyString(), anyString(), anyInt())).thenReturn("http://minio/get");

            FileDownloadResponse resp = service.download(5L);

            assertFalse(resp.watermark());
            verify(minioStorage).presignGet(eq("hris-files"), eq("tenant-1/hr/202601/u-a.pdf"), anyInt());
        }
    }
}

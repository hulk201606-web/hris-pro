package com.hris.platform.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.platform.config.MinioProperties;
import com.hris.platform.dto.FileDtos.FileDownloadResponse;
import com.hris.platform.dto.FileDtos.PresignUploadRequest;
import com.hris.platform.dto.FileDtos.PresignUploadResponse;
import com.hris.platform.entity.SysFileMeta;
import com.hris.platform.mapper.SysFileMetaMapper;
import com.hris.platform.storage.MinioStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FileService 单元测试（PLT-F-001/002 MinIO 直传预签名 + 文件元数据）。
 *
 * <p>最高价值守卫是buildObjectKey 内的文件名消毒
 * {@code replaceAll("[^a-zA-Z0-9._-]", "_")}：对象键形如
 * tenant-{id}/{module}/{yyyyMM}/{uuid}-{safeName}，若消毒失效，传入
 * {@code ../../secret} 这类文件名即可越出租户前缀读到其它租户对象，
 * 属跨租户数据泄露。故消毒用例以攻击载荷而非正常文件名为主。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileServiceObjectKeyTest {

    @Mock
    private SysFileMetaMapper fileMetaMapper;
    @Mock
    private MinioStorage minioStorage;

    private MinioProperties props;
    private FileService service;

    @BeforeEach
    void setUp() {
        props = new MinioProperties();
        props.setBucket("hris-files");
        service = new FileService(fileMetaMapper, minioStorage, props);
    }

    @AfterEach
    void clearTraceContext() {
        TraceContext.clear();
    }

    private void captureUploadedKey(PresignUploadRequest req) {
        when(fileMetaMapper.insert(any(SysFileMeta.class))).thenReturn(1);
        when(minioStorage.presignPut(anyString(), anyString(), anyInt())).thenReturn("http://minio/put");
        service.presignUpload(req);
    }

    private String capturedKey() {
        ArgumentCaptor<SysFileMeta> captor = ArgumentCaptor.forClass(SysFileMeta.class);
        verify(fileMetaMapper).insert(captor.capture());
        return captor.getValue().getObjectKey();
    }

    @Nested
    @DisplayName("文件名消毒：路径穿越与非法字符防线")
    class Sanitization {

        @Test
        @DisplayName("文件名含 ../ 路径穿越 → 斜杠被消毒，.. 无法构成独立路径段")
        void neutralizesPathTraversal() {
            TraceContext.setTenantId(1L);
            captureUploadedKey(new PresignUploadRequest(
                    "../../secret.txt", "text/plain", 1L, null, "contract"));

            String key = capturedKey();
            for (String segment : key.split("/")) {
                assertFalse("..".equals(segment) || ".".equals(segment) || segment.isEmpty(),
                        "对象键不得含独立的 .. 路径段，否则可越出租户前缀，实际分段=" + key);
            }
            assertTrue(key.startsWith("tenant-1/contract/"),
                    "穿越字符不得改变租户/模块前缀分段，实际=" + key);
        }

        @Test
        @DisplayName("文件名含绝对路径 /etc/passwd → 斜杠被消毒，不产生新路径段")
        void neutralizesAbsolutePath() {
            TraceContext.setTenantId(1L);
            captureUploadedKey(new PresignUploadRequest("/etc/passwd", "text/plain", 1L, null, "avatar"));

            assertFalse(capturedKey().contains("/etc"), "斜杠应被消毒，实际=" + capturedKey());
        }

        @Test
        @DisplayName("文件名含空格与中文等非法字符 → 全部替换为下划线")
        void neutralizesIllegalChars() {
            TraceContext.setTenantId(1L);
            captureUploadedKey(new PresignUploadRequest("我的 简历 v1.pdf", "application/pdf", 1L, null, "hr"));

            String key = capturedKey();
            assertTrue(key.endsWith("______v1.pdf"), "空格与中文应被消毒，实际=" + key);
        }

        @Test
        @DisplayName("合法文件名（字母数字点横线下划线）→ 原样保留，不被误伤")
        void keepsLegalFileName() {
            TraceContext.setTenantId(1L);
            captureUploadedKey(new PresignUploadRequest("contract_2026-v1.pdf", "application/pdf", 1L, null, "contract"));

            assertTrue(capturedKey().endsWith("contract_2026-v1.pdf"), "合法文件名不应被消毒");
        }
    }

    @Nested
    @DisplayName("对象键结构：租户隔离与分段规范（docs/08 §3）")
    class KeyStructure {

        @Test
        @DisplayName("对象键以 tenant-{id}/ 开头，租户前缀不可缺失")
        void startsWithTenantPrefix() {
            TraceContext.setTenantId(42L);
            captureUploadedKey(new PresignUploadRequest("a.txt", "text/plain", 1L, null, "contract"));

            assertTrue(capturedKey().startsWith("tenant-42/"), "实际=" + capturedKey());
        }

        @Test
        @DisplayName("租户上下文缺失 → 退化为 tenant-0，不抛 NPE")
        void fallsBackToTenantZero() {
            TraceContext.setTenantId(null);
            captureUploadedKey(new PresignUploadRequest("a.txt", "text/plain", 1L, null, "contract"));

            assertTrue(capturedKey().startsWith("tenant-0/"), "实际=" + capturedKey());
        }

        @Test
        @DisplayName("module 为空白 → 回落 general 分段")
        void fallsBackToGeneralModule() {
            TraceContext.setTenantId(1L);
            captureUploadedKey(new PresignUploadRequest("a.txt", "text/plain", 1L, null, "   "));

            assertTrue(capturedKey().contains("/general/"), "实际=" + capturedKey());
        }

        @Test
        @DisplayName("module 大写带空白 → 归一化为小写无空格分段")
        void normalizesModule() {
            TraceContext.setTenantId(1L);
            captureUploadedKey(new PresignUploadRequest("a.txt", "text/plain", 1L, null, "  HR  "));

            assertTrue(capturedKey().contains("/hr/"), "module 应小写化，实际=" + capturedKey());
        }

        @Test
        @DisplayName("对象键含 yyyyMM 日期分段 + uuid 前缀，避免同名覆盖")
        void includesDateAndUuidPrefix() {
            TraceContext.setTenantId(1L);
            captureUploadedKey(new PresignUploadRequest("a.txt", "text/plain", 1L, null, "hr"));

            String key = capturedKey();
            assertTrue(key.matches("tenant-1/hr/\\d{6}/[0-9a-f]{32}-a\\.txt"),
                    "结构应为 tenant-1/hr/{yyyyMM}/{uuid}-{safeName}，实际=" + key);
        }
    }

    @Nested
    @DisplayName("元数据与降级")
    class MetaAndDegrade {

        @Test
        @DisplayName("sizeBytes 未传 → 落库为 0，不写 null")
        void defaultsSizeToZero() {
            TraceContext.setTenantId(1L);
            captureUploadedKey(new PresignUploadRequest("a.txt", "text/plain", null, null, "hr"));

            ArgumentCaptor<SysFileMeta> captor = ArgumentCaptor.forClass(SysFileMeta.class);
            verify(fileMetaMapper).insert(captor.capture());
            assertEquals(0L, captor.getValue().getSizeBytes());
        }

        @Test
        @DisplayName("MinIO 未就绪（presign 返 null）→ degraded=true，调用方可识别降级")
        void marksDegradedWhenPresignUnavailable() {
            TraceContext.setTenantId(1L);
            when(fileMetaMapper.insert(any(SysFileMeta.class))).thenReturn(1);
            when(minioStorage.presignPut(anyString(), anyString(), anyInt())).thenReturn(null);

            PresignUploadResponse resp = service.presignUpload(new PresignUploadRequest(
                    "a.txt", "text/plain", 1L, null, "hr"));

            assertTrue(resp.degraded(), "预签名为空应标记降级");
        }

        @Test
        @DisplayName("元数据落库用配置的桶名，文件名单独 trim")
        void persistsBucketAndTrimmedName() {
            TraceContext.setTenantId(1L);
            captureUploadedKey(new PresignUploadRequest("  a.txt  ", "text/plain", 1L, null, "hr"));

            ArgumentCaptor<SysFileMeta> captor = ArgumentCaptor.forClass(SysFileMeta.class);
            verify(fileMetaMapper).insert(captor.capture());
            assertEquals("hris-files", captor.getValue().getBucket());
            assertEquals("a.txt", captor.getValue().getFileName());
        }
    }

    @Nested
    @DisplayName("下载：存在性守卫")
    class Download {

        @Test
        @DisplayName("文件不存在 → NOT_FOUND")
        void fileNotFound() {
            when(fileMetaMapper.selectById(anyLong())).thenReturn(null);

            BizException e = assertThrows(BizException.class, () -> service.download(404L));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("下载签名成功 → degraded=false")
        void downloadSucceeds() {
            SysFileMeta m = new SysFileMeta();
            m.setId(5L);
            m.setBucket("hris-files");
            m.setObjectKey("tenant-1/hr/202601/u-a.txt");
            when(fileMetaMapper.selectById(5L)).thenReturn(m);
            when(minioStorage.presignGet(anyString(), anyString(), anyInt()))
                    .thenReturn("http://minio/get");

            FileDownloadResponse resp = service.download(5L);

            assertFalse(resp.degraded());
            assertEquals("http://minio/get", resp.downloadUrl());
            verify(minioStorage).presignGet(eq("hris-files"),
                    eq("tenant-1/hr/202601/u-a.txt"), anyInt());
        }
    }
}
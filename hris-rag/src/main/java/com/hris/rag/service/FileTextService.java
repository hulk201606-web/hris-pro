package com.hris.rag.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.rag.client.PlatformFileClient;
import com.hris.rag.dto.FileTextDtos.ExtractTextResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 文件文本抽取编排（批 12 #28）：两种入参统一到 {@link FileTextExtractor}。
 *
 * <ul>
 *   <li>直传：multipart 文件（前端/调用方已持有字节，最简路径）；</li>
 *   <li>按 fileId：经 platform 文件服务取预签名 URL 后再下载字节（recruit 侧只持有
 *       {@code rcu_resume.file_id}，无法直传）。</li>
 * </ul>
 *
 * <p>下载域限制：单文件 20MB 上限（简历附件常态远小于此），超限直接报参数错误，
 * 防止大文件把解析线程拖死。</p>
 */
@Service
public class FileTextService {

    private static final Logger log = LoggerFactory.getLogger(FileTextService.class);

    /** 单文件上限 20MB。 */
    static final long MAX_BYTES = 20L * 1024 * 1024;

    private final FileTextExtractor extractor;
    private final PlatformFileClient fileClient;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public FileTextService(FileTextExtractor extractor, PlatformFileClient fileClient) {
        this.extractor = extractor;
        this.fileClient = fileClient;
    }

    /** 直传抽取。 */
    public ExtractTextResponse extract(MultipartFile file) {
        return extractor.extract(file);
    }

    /**
     * 按平台文件 ID 抽取：取预签名地址 → 下载 → 抽取。
     * 平台不可用/无权限/下载失败一律抛 RAG_ERROR（recruit 侧据此降级回原文法）。
     */
    public ExtractTextResponse extractByFileId(Long fileId, String fileName) {
        if (fileId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "fileId 不能为空");
        }
        String url = presignedUrl(fileId);
        byte[] content = download(url);
        return extractor.extract(content, fileName);
    }

    private String presignedUrl(Long fileId) {
        R<Map<String, Object>> resp;
        try {
            resp = fileClient.download(fileId);
        } catch (Exception e) {
            log.warn("platform 文件服务不可用 fileId={}: {}", fileId, e.getMessage());
            throw new BizException(ErrorCode.RAG_ERROR, "平台文件服务不可用：" + e.getMessage());
        }
        Map<String, Object> data = resp == null ? null : resp.getData();
        Object url = data == null ? null : data.get("downloadUrl");
        if (url == null || url.toString().isBlank()) {
            throw new BizException(ErrorCode.RAG_ERROR,
                    "平台文件服务未返回下载地址（fileId=" + fileId + "，可能 MinIO 未就绪）");
        }
        return url.toString();
    }

    private byte[] download(String url) {
        try {
            HttpResponse<byte[]> resp = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new BizException(ErrorCode.RAG_ERROR, "文件下载失败：HTTP " + resp.statusCode());
            }
            byte[] body = resp.body();
            if (body != null && body.length > MAX_BYTES) {
                throw new BizException(ErrorCode.PARAM_INVALID, "文件超过 20MB 上限，拒绝解析");
            }
            return body;
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.RAG_ERROR, "文件下载被中断");
        } catch (Exception e) {
            throw new BizException(ErrorCode.RAG_ERROR, "文件下载失败：" + e.getMessage());
        }
    }
}

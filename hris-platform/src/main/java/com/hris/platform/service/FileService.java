package com.hris.platform.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.platform.config.MinioProperties;
import com.hris.platform.dto.FileDtos.FileDownloadResponse;
import com.hris.platform.dto.FileDtos.FileMetaResponse;
import com.hris.platform.dto.FileDtos.PresignUploadRequest;
import com.hris.platform.dto.FileDtos.PresignUploadResponse;
import com.hris.platform.entity.SysFileMeta;
import com.hris.platform.mapper.SysFileMetaMapper;
import com.hris.platform.storage.MinioStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 统一文件服务（PLT-F-001/002）：MinIO 直传预签名 + 文件元数据 + 下载签名。
 *
 * <p>PLT-F-002 深化：download 落审计（失败仅告警不影响下载）；watermarkPolicy
 * 开启时在下载响应中带 watermark=true 标记并写入审计行（MVP 语义：标记 + 审计，
 * 不改写 MinIO presign 响应头）。</p>
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private static final int UPLOAD_EXPIRY_SECONDS = 3600;
    private static final int DOWNLOAD_EXPIRY_SECONDS = 600;

    /** 水印策略的「开启」取值集合（其余取值/空白视为未开启）。 */
    private static final Set<String> WATERMARK_ON = Set.of("ON", "ENABLED", "TRUE", "1", "WATERMARK");

    private final SysFileMetaMapper fileMetaMapper;
    private final MinioStorage minioStorage;
    private final MinioProperties minioProperties;
    private final AuditLogService auditLogService;

    public FileService(SysFileMetaMapper fileMetaMapper, MinioStorage minioStorage,
                       MinioProperties minioProperties, AuditLogService auditLogService) {
        this.fileMetaMapper = fileMetaMapper;
        this.minioStorage = minioStorage;
        this.minioProperties = minioProperties;
        this.auditLogService = auditLogService;
    }

    /** 生成 MinIO 直传预签名 URL，同时落文件元数据。 */
    public PresignUploadResponse presignUpload(PresignUploadRequest req) {
        String bucket = minioProperties.getBucket();
        String objectKey = buildObjectKey(req.fileName(), req.module());

        SysFileMeta meta = new SysFileMeta();
        meta.setBucket(bucket);
        meta.setObjectKey(objectKey);
        meta.setFileName(req.fileName().trim());
        meta.setContentType(req.contentType());
        meta.setSizeBytes(req.sizeBytes() == null ? 0L : req.sizeBytes());
        meta.setWatermarkPolicy(req.watermarkPolicy());
        fileMetaMapper.insert(meta);

        String uploadUrl = minioStorage.presignPut(bucket, objectKey, UPLOAD_EXPIRY_SECONDS);
        return new PresignUploadResponse(meta.getId(), objectKey, uploadUrl, uploadUrl == null);
    }

    /** 按文件 ID 查询元数据。 */
    public FileMetaResponse getMeta(Long id) {
        return toMeta(require(id));
    }

    /**
     * 生成下载预签名 URL；同时落下载审计与水印标记（PLT-F-002 深化）。
     * watermark 开启时在签名 URL 上附带 response-content-disposition 查询参数
     * （MinIO 将其作为响应头返回，下载文件名带「[水印]」前缀）；审计失败仅告警不影响下载。
     */
    public FileDownloadResponse download(Long id) {
        SysFileMeta meta = require(id);
        boolean watermark = isWatermarkOn(meta.getWatermarkPolicy());
        String url = watermark
                ? minioStorage.presignGet(meta.getBucket(), meta.getObjectKey(),
                        DOWNLOAD_EXPIRY_SECONDS, watermarkDisposition(meta.getFileName()))
                : minioStorage.presignGet(meta.getBucket(), meta.getObjectKey(), DOWNLOAD_EXPIRY_SECONDS);
        appendDownloadAudit(meta, watermark);
        return new FileDownloadResponse(meta.getId(), url, url == null, watermark);
    }

    /** 水印下载的 content-disposition 签名参数（文件名消毒防头注入）。 */
    private Map<String, String> watermarkDisposition(String fileName) {
        String safe = (fileName == null ? "file" : fileName)
                .replaceAll("[\\r\\n\"\\\\]", "_");
        return Map.of("response-content-disposition", "attachment; filename=\"[水印] " + safe + "\"");
    }

    private boolean isWatermarkOn(String policy) {
        return policy != null && WATERMARK_ON.contains(policy.trim().toUpperCase());
    }

    /** 下载审计行：module=file/op=download/detail=fileId+对象键+watermarkPolicy。 */
    private void appendDownloadAudit(SysFileMeta meta, boolean watermark) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("fileId", meta.getId());
            detail.put("objectKey", meta.getObjectKey());
            detail.put("watermarkPolicy", meta.getWatermarkPolicy());
            detail.put("watermark", watermark);
            auditLogService.append("file:download", "file/" + meta.getId(), detail, null);
        } catch (Exception e) {
            log.warn("download audit failed, fileId={}: {}", meta.getId(), e.getMessage());
        }
    }

    private SysFileMeta require(Long id) {
        SysFileMeta meta = fileMetaMapper.selectById(id);
        if (meta == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return meta;
    }

    private FileMetaResponse toMeta(SysFileMeta m) {
        return new FileMetaResponse(m.getId(), m.getBucket(), m.getObjectKey(), m.getFileName(),
                m.getContentType(), m.getSizeBytes(), m.getWatermarkPolicy(), m.getCreatedAt());
    }

    /** 对象键：tenant-{id}/{module}/{yyyyMM}/{uuid}-{safeName}（docs/08 §3 桶路径 tenant-{id}/ 约定）。 */
    private String buildObjectKey(String fileName, String module) {
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null) {
            tenantId = 0L;
        }
        String mod = (module == null || module.isBlank()) ? "general" : module.trim().toLowerCase();
        String yyyyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return "tenant-" + tenantId + "/" + mod + "/" + yyyyMM + "/"
                + UUID.randomUUID().toString().replace("-", "") + "-" + safeName;
    }
}

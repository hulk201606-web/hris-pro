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
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 统一文件服务（PLT-F-001/002）：MinIO 直传预签名 + 文件元数据 + 下载签名。
 */
@Service
public class FileService {

    private static final int UPLOAD_EXPIRY_SECONDS = 3600;
    private static final int DOWNLOAD_EXPIRY_SECONDS = 600;

    private final SysFileMetaMapper fileMetaMapper;
    private final MinioStorage minioStorage;
    private final MinioProperties minioProperties;

    public FileService(SysFileMetaMapper fileMetaMapper, MinioStorage minioStorage,
                       MinioProperties minioProperties) {
        this.fileMetaMapper = fileMetaMapper;
        this.minioStorage = minioStorage;
        this.minioProperties = minioProperties;
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

    /** 生成下载预签名 URL。 */
    public FileDownloadResponse download(Long id) {
        SysFileMeta meta = require(id);
        String url = minioStorage.presignGet(meta.getBucket(), meta.getObjectKey(), DOWNLOAD_EXPIRY_SECONDS);
        return new FileDownloadResponse(meta.getId(), url, url == null);
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

package com.hris.platform.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 文件服务 DTO（PLT-F-001/002）。
 */
public final class FileDtos {

    private FileDtos() {
    }

    /** 预签名上传请求 */
    public record PresignUploadRequest(
            @NotBlank(message = "文件名不能为空") String fileName,
            String contentType,
            Long sizeBytes,
            String watermarkPolicy,
            String module) {
    }

    /** 预签名上传响应；degraded=true 表示 MinIO 未就绪，uploadUrl 为占位 */
    public record PresignUploadResponse(
            Long fileId,
            String objectKey,
            String uploadUrl,
            boolean degraded) {
    }

    /** 文件元数据响应 */
    public record FileMetaResponse(
            Long id,
            String bucket,
            String objectKey,
            String fileName,
            String contentType,
            Long sizeBytes,
            String watermarkPolicy,
            LocalDateTime createdAt) {
    }

    /** 文件下载响应（预签名 URL） */
    public record FileDownloadResponse(
            Long fileId,
            String downloadUrl,
            boolean degraded) {
    }
}

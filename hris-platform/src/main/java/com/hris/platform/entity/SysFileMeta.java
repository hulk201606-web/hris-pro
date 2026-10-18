package com.hris.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 文件元数据（bucket/object_key/水印策略，PLT-F-001/002）。文件内容不落库，走 MinIO 引用。
 */
@TableName("sys_file_meta")
public class SysFileMeta extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** MinIO 桶 */
    private String bucket;

    /** 对象键 */
    private String objectKey;

    /** 原始文件名 */
    private String fileName;

    /** 内容类型 */
    private String contentType;

    /** 文件大小（字节） */
    private Long sizeBytes;

    /** 水印策略（预留字段，合同/薪酬类敏感文件下载时启用） */
    private String watermarkPolicy;

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getWatermarkPolicy() {
        return watermarkPolicy;
    }

    public void setWatermarkPolicy(String watermarkPolicy) {
        this.watermarkPolicy = watermarkPolicy;
    }
}

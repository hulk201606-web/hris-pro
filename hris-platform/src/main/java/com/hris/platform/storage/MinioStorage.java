package com.hris.platform.storage;

import com.hris.platform.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储适配（预签名 URL，PLT-F-001/002）。
 *
 * <p>降级策略：未配置 endpoint 或探测不可达时，{@link #isReady()} 返回 false，
 * 预签名返回 {@code null}，由 FileService 降级为占位 URL 并记录明确日志。</p>
 */
@Component
public class MinioStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioStorage.class);

    private final MinioProperties props;

    /** 客户端懒加载；available==null 表示尚未探测 */
    private volatile MinioClient client;
    private volatile Boolean available;

    public MinioStorage(MinioProperties props) {
        this.props = props;
    }

    /** 是否可用：已配置且探测可达（懒探测一次 + 缓存）。 */
    public boolean isReady() {
        if (!isConfigured()) {
            return false;
        }
        if (available == null) {
            synchronized (this) {
                if (available == null) {
                    available = probe();
                }
            }
        }
        return available;
    }

    /** 生成上传预签名 URL；不可用/异常时返回 null（由调用方降级）。 */
    public String presignPut(String bucket, String objectKey, int expirySeconds) {
        return presign(Method.PUT, bucket, objectKey, expirySeconds);
    }

    /** 生成下载预签名 URL；不可用/异常时返回 null（由调用方降级）。 */
    public String presignGet(String bucket, String objectKey, int expirySeconds) {
        return presign(Method.GET, bucket, objectKey, expirySeconds);
    }

    private String presign(Method method, String bucket, String objectKey, int expirySeconds) {
        if (!isReady()) {
            return null;
        }
        try {
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(method)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expirySeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO presign {} failed, object={}: {}", method, objectKey, e.getMessage());
            return null;
        }
    }

    private boolean isConfigured() {
        return props.getEndpoint() != null && !props.getEndpoint().isBlank()
                && props.getAccessKey() != null && !props.getAccessKey().isBlank()
                && props.getSecretKey() != null && !props.getSecretKey().isBlank()
                && props.getBucket() != null && !props.getBucket().isBlank();
    }

    private boolean probe() {
        try {
            MinioClient c = client();
            boolean exists = c.bucketExists(BucketExistsArgs.builder().bucket(props.getBucket()).build());
            if (!exists) {
                c.makeBucket(MakeBucketArgs.builder().bucket(props.getBucket()).build());
                log.info("MinIO bucket auto-created: {}", props.getBucket());
            }
            return true;
        } catch (Exception e) {
            log.warn("MinIO unavailable, presign will degrade: {}", e.getMessage());
            return false;
        }
    }

    private MinioClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = MinioClient.builder()
                            .endpoint(props.getEndpoint())
                            .credentials(props.getAccessKey(), props.getSecretKey())
                            .build();
                }
            }
        }
        return client;
    }
}

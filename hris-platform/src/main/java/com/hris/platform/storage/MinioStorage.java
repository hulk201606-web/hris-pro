package com.hris.platform.storage;

import com.hris.platform.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Map;
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
        return presign(Method.GET, bucket, objectKey, expirySeconds, null);
    }

    /**
     * 生成下载预签名 URL 并附带签名查询参数（PLT-F-002 深化）：
     * watermark 开启时传 {@code response-content-disposition}，MinIO 会将其作为
     * 响应头返回（覆盖下载文件名），SDK 按 V4 签名一并纳入签名。
     */
    public String presignGet(String bucket, String objectKey, int expirySeconds,
                             Map<String, String> extraQueryParams) {
        return presign(Method.GET, bucket, objectKey, expirySeconds, extraQueryParams);
    }

    /**
     * 服务端直接写入对象（异步导出执行器用）。
     *
     * <p>返回 false 表示「文件服务当前不可用」，调用方必须据此保持任务
     * PENDING 等待下次调度，而不能把任务标成 DONE——那会造出一个
     * 永远下载不到的导出结果。</p>
     */
    public boolean putObject(String bucket, String objectKey, byte[] content, String contentType) {
        if (!isReady()) {
            return false;
        }
        try {
            client().putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
            return true;
        } catch (Exception e) {
            log.warn("MinIO putObject failed, object={}: {}", objectKey, e.getMessage());
            return false;
        }
    }

    private String presign(Method method, String bucket, String objectKey, int expirySeconds) {
        return presign(method, bucket, objectKey, expirySeconds, null);
    }

    private String presign(Method method, String bucket, String objectKey, int expirySeconds,
                           Map<String, String> extraQueryParams) {
        if (!isReady()) {
            return null;
        }
        try {
            GetPresignedObjectUrlArgs.Builder builder = GetPresignedObjectUrlArgs.builder()
                    .method(method)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expirySeconds, TimeUnit.SECONDS);
            if (extraQueryParams != null && !extraQueryParams.isEmpty()) {
                builder.extraQueryParams(extraQueryParams);
            }
            return client().getPresignedObjectUrl(builder.build());
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

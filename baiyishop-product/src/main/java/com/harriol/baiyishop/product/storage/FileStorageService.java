package com.harriol.baiyishop.product.storage;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import io.minio.BucketExistsArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 图片上传到 MinIO（REQ-203）。
 * <p>要点：
 * <ol>
 *   <li>只接受图片类型、限制大小，避免把任意文件塞进对象存储</li>
 *   <li>对象名按「业务目录 / 日期 / UUID.扩展名」生成，避免同名覆盖与中文名问题</li>
 *   <li>桶不存在时自动创建并设为**匿名只读**：图片要被前台 {@code <img>} 直接加载</li>
 * </ol>
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /** 允许的图片类型 → 扩展名 */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif");

    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 桶只需初始化一次，后续上传不再重复探测 */
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public FileStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * 上传一张图片。
     *
     * @param file   上传的文件
     * @param folder 业务目录，如 {@code product} / {@code banner}
     */
    public StoredFile uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE);
        }
        String contentType = normalizeContentType(file.getContentType(), file.getOriginalFilename());
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        String objectName = folder + "/" + LocalDate.now().format(DATE_DIR) + "/"
                + UUID.randomUUID().toString().replace("-", "") + extensionOf(contentType);
        try {
            ensureBucket();
            try (InputStream in = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(properties.bucket())
                        .object(objectName)
                        .contentType(contentType)
                        .stream(in, file.getSize(), -1)
                        .build());
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("上传图片失败 objectName={} size={}", objectName, file.getSize(), ex);
            // 凭据不匹配是最常见的接入问题，直接把原因透出来，避免只看到「上传失败」
            if (ex instanceof ErrorResponseException minioError) {
                String minioCode = minioError.errorResponse() == null ? "" : minioError.errorResponse().code();
                if ("InvalidAccessKeyId".equals(minioCode) || "SignatureDoesNotMatch".equals(minioCode)) {
                    throw new BizException(ErrorCode.FILE_UPLOAD_FAILED,
                            "对象存储凭据无效：请确认 MinIO 容器与本地配置里的 MINIO_ROOT_USER / MINIO_ROOT_PASSWORD 一致");
                }
            }
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        String url = properties.baseUrlForPublic() + "/" + properties.bucket() + "/" + objectName;
        log.info("图片已上传 bucket={} objectName={} size={}", properties.bucket(), objectName, file.getSize());
        return new StoredFile(url, objectName, file.getSize(), contentType);
    }

    /** 桶不存在则创建，并设置为匿名只读（图片需要被浏览器直接访问） */
    private void ensureBucket() throws Exception {
        if (bucketReady.get()) {
            return;
        }
        synchronized (bucketReady) {
            if (bucketReady.get()) {
                return;
            }
            String bucket = properties.bucket();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("已创建对象存储桶 {}", bucket);
            }
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(publicReadPolicy(bucket))
                    .build());
            bucketReady.set(true);
        }
    }

    /** 仅允许匿名读取对象（GetObject），其余动作仍需凭证 */
    private String publicReadPolicy(String bucket) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """.formatted(bucket);
    }

    /** 有些客户端不带 content-type，按扩展名兜底推断 */
    private String normalizeContentType(String contentType, String originalName) {
        if (contentType != null && !contentType.isBlank() && !"application/octet-stream".equals(contentType)) {
            return contentType.toLowerCase(Locale.ROOT);
        }
        String name = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ? "image/jpeg" : "";
    }

    private String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }
}

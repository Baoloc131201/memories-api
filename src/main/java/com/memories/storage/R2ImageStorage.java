package com.memories.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;

/**
 * Lưu ảnh trên Cloudflare R2 qua API tương thích S3. Bucket để private,
 * ảnh vẫn chỉ đi qua BE nên giữ nguyên việc kiểm tra quyền sở hữu.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "r2")
public class R2ImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(R2ImageStorage.class);

    private final S3Client s3;
    private final String bucket;

    public R2ImageStorage(@Value("${app.storage.r2.account-id}") String accountId,
                          @Value("${app.storage.r2.access-key}") String accessKey,
                          @Value("${app.storage.r2.secret-key}") String secretKey,
                          @Value("${app.storage.r2.bucket}") String bucket) {
        if (accountId.isBlank() || accessKey.isBlank() || secretKey.isBlank() || bucket.isBlank()) {
            throw new IllegalStateException("STORAGE_TYPE=r2 cần đủ R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, R2_BUCKET");
        }
        this.bucket = bucket;
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                // SDK mới mặc định gửi checksum CRC mà R2 chưa hỗ trợ đủ, chỉ bật khi bắt buộc
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
        log.info("Lưu ảnh trên Cloudflare R2, bucket {}", bucket);
    }

    @Override
    public void save(String key, InputStream in, long size, String contentType) {
        s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType).contentLength(size),
                RequestBody.fromInputStream(in, size));
    }

    @Override
    public Optional<StoredImage> load(String key) {
        try {
            ResponseInputStream<GetObjectResponse> in = s3.getObject(b -> b.bucket(bucket).key(key));
            Long size = in.response().contentLength();
            return Optional.of(new StoredImage(new InputStreamResource(in), size != null ? size : -1));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(b -> b.bucket(bucket).key(key));
    }

    public boolean exists(String key) {
        try {
            s3.headObject(b -> b.bucket(bucket).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @PreDestroy
    void close() {
        s3.close();
    }
}

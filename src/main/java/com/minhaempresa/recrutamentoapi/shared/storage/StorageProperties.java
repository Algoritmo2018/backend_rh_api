package com.minhaempresa.recrutamentoapi.shared.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * File storage ({@code app.storage.*}).
 *
 * @param type {@code s3} (S3 or MinIO, the default) or {@code local} (local disk)
 */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(@NotNull Type type, @Valid Local local, @Valid S3 s3) {

    public enum Type {
        LOCAL,
        S3
    }

    /** @param root folder where files are written; every key must resolve inside it */
    public record Local(String root) {}

    /**
     * @param endpoint S3-compatible endpoint (MinIO); empty for AWS S3
     * @param region region (MinIO accepts any, e.g. {@code us-east-1})
     * @param bucket private bucket of the application files
     * @param accessKey empty → the AWS default credentials chain (environment, instance role…)
     * @param secretKey secret of {@code accessKey}; only from the environment, never in git
     * @param pathStyle {@code true} for MinIO ({@code endpoint/bucket/key})
     * @param serverSideEncryption encrypt every object at rest (SSE-S3, {@code AES256}); MinIO needs
     *     {@code MINIO_KMS_SECRET_KEY}
     * @param createBucket create the bucket on startup when missing (development and tests only)
     */
    public record S3(
            String endpoint,
            String region,
            String bucket,
            String accessKey,
            String secretKey,
            boolean pathStyle,
            boolean serverSideEncryption,
            boolean createBucket) {

        @Override
        public String toString() {
            return "S3(endpoint=" + endpoint + ", bucket=" + bucket + ")";
        }
    }
}

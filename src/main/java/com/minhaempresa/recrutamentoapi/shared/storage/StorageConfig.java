package com.minhaempresa.recrutamentoapi.shared.storage;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/** Chooses the {@link StorageService} implementation ({@code app.storage.type}). */
@Slf4j
@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local")
    StorageService localStorageService(StorageProperties properties) {
        if (properties.local() == null || properties.local().root() == null || properties.local().root().isBlank()) {
            throw new IllegalStateException("app.storage.local.root is required when app.storage.type=local");
        }
        return new LocalStorageService(properties);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3", matchIfMissing = true)
    S3Client s3Client(StorageProperties properties) {
        StorageProperties.S3 s3 = requireS3(properties);
        var builder = S3Client.builder()
                .region(Region.of(s3.region()))
                .credentialsProvider(credentials(s3))
                .forcePathStyle(s3.pathStyle())
                // Checksums only when the API requires them (S3-compatible servers differ on the new defaults)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED);
        if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.endpoint()));
        }
        S3Client client = builder.build();
        if (s3.createBucket()) {
            createBucketIfMissing(client, s3.bucket());
        }
        return client;
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3", matchIfMissing = true)
    StorageService s3StorageService(S3Client client, StorageProperties properties) {
        return new S3StorageService(client, requireS3(properties));
    }

    private static AwsCredentialsProvider credentials(StorageProperties.S3 s3) {
        if (s3.accessKey() == null || s3.accessKey().isBlank()) {
            return DefaultCredentialsProvider.builder().build();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey(), s3.secretKey()));
    }

    /** New buckets are private (no policy): objects are only reachable with the credentials. */
    private static void createBucketIfMissing(S3Client client, String bucket) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created storage bucket {}", bucket);
        }
    }

    private static StorageProperties.S3 requireS3(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.s3();
        if (s3 == null || s3.bucket() == null || s3.bucket().isBlank() || s3.region() == null || s3.region().isBlank()) {
            throw new IllegalStateException("app.storage.s3.bucket and app.storage.s3.region are required when app.storage.type=s3");
        }
        return s3;
    }
}

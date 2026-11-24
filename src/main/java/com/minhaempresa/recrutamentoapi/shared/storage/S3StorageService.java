package com.minhaempresa.recrutamentoapi.shared.storage;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Files in a private S3 / MinIO bucket. Objects are never public: they are always read through the
 * API, which checks the permission first. Every object is encrypted at rest (SSE-S3) when configured.
 * Keys are generated here and validated on every read, so a crafted key never reaches the bucket.
 */
@Slf4j
public class S3StorageService implements StorageService {

    private static final Pattern CATEGORY = Pattern.compile("[a-z0-9-]+");
    private static final Pattern KEY = Pattern.compile("[a-z0-9-]+/[0-9a-f-]{36}\\.[a-z0-9]+");

    private final S3Client client;
    private final String bucket;
    private final boolean serverSideEncryption;

    public S3StorageService(S3Client client, StorageProperties.S3 properties) {
        this.client = client;
        this.bucket = properties.bucket();
        this.serverSideEncryption = properties.serverSideEncryption();
    }

    @Override
    public StoredFile store(String category, FileType type, byte[] content) {
        if (!CATEGORY.matcher(category).matches()) {
            throw new IllegalArgumentException("Invalid storage category: " + category);
        }
        String key = category + "/" + UUID.randomUUID() + "." + type.extension();
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(type.mimeType())
                .contentLength((long) content.length);
        if (serverSideEncryption) {
            request.serverSideEncryption(ServerSideEncryption.AES256);
        }
        client.putObject(request.build(), RequestBody.fromBytes(content));
        return new StoredFile(key, type, content.length, sha256(content));
    }

    @Override
    public Resource load(String key) {
        requireValidKey(key);
        try {
            return new ByteArrayResource(client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray());
        } catch (NoSuchKeyException e) {
            throw new NoSuchElementException("No stored file " + key);
        }
    }

    @Override
    public void delete(String key) {
        requireValidKey(key);
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    private static void requireValidKey(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

package com.minhaempresa.recrutamentoapi;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL, Mailpit and MinIO for integration tests, same images as docker-compose.yml. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String MINIO_IMAGE = "cgr.dev/chainguard/minio@sha256:f2e56aceb4d38ad9b05c901270946e2353b19f86ba4396f25cf75b15b455b38c";
    public static final String MINIO_BUCKET = "recrutamento-test";
    public static final String POSTGRES_IMAGE = "postgres:17-alpine";
    public static final String MAILPIT_IMAGE = "axllent/mailpit:v1.34";
    public static final int MAILPIT_SMTP_PORT = 1025;
    public static final int MAILPIT_HTTP_PORT = 8025;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE));
    }

    @Bean
    GenericContainer<?> mailpitContainer() {
        return new GenericContainer<>(DockerImageName.parse(MAILPIT_IMAGE))
                .withExposedPorts(MAILPIT_SMTP_PORT, MAILPIT_HTTP_PORT)
                // Keep every message (default: only the newest 500). Emails "to every administrator"
                // reach all admins created by earlier tests, and none may be pruned before it is read.
                .withEnv("MP_MAX_MESSAGES", "0")
                .waitingFor(Wait.forHttp("/api/v1/info").forPort(MAILPIT_HTTP_PORT));
    }

    @Bean
    DynamicPropertyRegistrar mailpitProperties(GenericContainer<?> mailpitContainer) {
        return registry -> {
            registry.add("spring.mail.host", mailpitContainer::getHost);
            registry.add("spring.mail.port", () -> mailpitContainer.getMappedPort(MAILPIT_SMTP_PORT));
        };
    }

    /**
     * S3-compatible storage. The encryption key (SSE) is generated for each run: no key is ever written
     * in the repository.
     */
    @Bean
    MinIOContainer minioContainer() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return new MinIOContainer(DockerImageName.parse(MINIO_IMAGE).asCompatibleSubstituteFor("minio/minio"))
                .withUserName("test-access-key")
                .withPassword("test-secret-key-0123456789")
                .withEnv("MINIO_KMS_SECRET_KEY", "test-key:" + Base64.getEncoder().encodeToString(key));
    }

    @Bean
    DynamicPropertyRegistrar minioProperties(MinIOContainer minioContainer) {
        return registry -> {
            registry.add("app.storage.type", () -> "s3");
            registry.add("app.storage.s3.endpoint", minioContainer::getS3URL);
            registry.add("app.storage.s3.access-key", minioContainer::getUserName);
            registry.add("app.storage.s3.secret-key", minioContainer::getPassword);
            registry.add("app.storage.s3.bucket", () -> MINIO_BUCKET);
            registry.add("app.storage.s3.path-style", () -> "true");
            registry.add("app.storage.s3.create-bucket", () -> "true");
        };
    }
}

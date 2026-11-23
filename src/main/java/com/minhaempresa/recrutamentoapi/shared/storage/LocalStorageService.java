package com.minhaempresa.recrutamentoapi.shared.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Files on the local disk under {@code app.storage.local.root}. Every key is resolved and checked to
 * stay inside the root, so a crafted key ({@code ../../etc/passwd}) can never escape it.
 */
@Service
public class LocalStorageService implements StorageService {

    private static final Pattern CATEGORY = Pattern.compile("[a-z0-9-]+");

    private final Path root;

    public LocalStorageService(StorageProperties properties) {
        this.root = Path.of(properties.local().root()).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(String category, FileType type, byte[] content) {
        if (!CATEGORY.matcher(category).matches()) {
            throw new IllegalArgumentException("Invalid storage category: " + category);
        }
        String key = category + "/" + UUID.randomUUID() + "." + type.extension();
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store " + key, e);
        }
        return new StoredFile(key, type, content.length, sha256(content));
    }

    @Override
    public Resource load(String key) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            throw new NoSuchElementException("No stored file " + key);
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete " + key, e);
        }
    }

    /** Absolute path of a key, refusing anything outside the root. */
    Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("Storage key outside the storage root");
        }
        return path;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

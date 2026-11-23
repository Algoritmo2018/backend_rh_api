package com.minhaempresa.recrutamentoapi.shared.storage;

import org.springframework.core.io.Resource;

/**
 * Stores uploaded files. The key is always generated here ({@code <category>/<uuid>.<ext>}, the
 * extension coming from the detected type), never from the name sent by the client; the original name
 * is only metadata kept by the caller.
 *
 * <p>Implementations: {@link LocalStorageService} (local disk, dev and until stage 6); S3/MinIO in
 * stage 6.
 */
public interface StorageService {

    /**
     * @param category lower-case folder, e.g. {@code company-logo}
     * @param type type detected from the content
     * @param content the file bytes
     */
    StoredFile store(String category, FileType type, byte[] content);

    /** @throws java.util.NoSuchElementException if the key does not exist */
    Resource load(String key);

    /** Deletes the file; does nothing if it does not exist. */
    void delete(String key);
}

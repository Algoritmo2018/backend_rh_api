package com.minhaempresa.recrutamentoapi.shared.storage;

import java.util.Arrays;
import java.util.Optional;

/**
 * File types accepted by the API, detected from the first bytes of the content (never from the
 * declared {@code Content-Type} or the file name). More types (PDF, DOCX) arrive in stage 6.
 */
public enum FileType {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final String mimeType;
    private final String extension;

    FileType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public String extension() {
        return extension;
    }

    /** Type of the content, if it is one of the supported formats. */
    public static Optional<FileType> detect(byte[] content) {
        if (startsWith(content, PNG_SIGNATURE, 0)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, JPEG_SIGNATURE, 0)) {
            return Optional.of(JPEG);
        }
        // RIFF....WEBP
        if (startsWith(content, "RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0)
                && startsWith(content, "WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 8)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, byte[] prefix, int offset) {
        return content != null
                && content.length >= offset + prefix.length
                && Arrays.equals(content, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}

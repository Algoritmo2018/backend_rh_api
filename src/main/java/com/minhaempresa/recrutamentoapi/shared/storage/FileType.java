package com.minhaempresa.recrutamentoapi.shared.storage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * File types accepted by the API, detected from the first bytes of the content (never from the
 * declared {@code Content-Type} or the file name). Each use restricts the types it accepts (the logo
 * only images, a photo only PNG/JPEG…).
 *
 * <p>Not accepted on purpose: the legacy {@code .doc} (OLE container, the classic macro carrier, not
 * reliably told apart from other OLE files) and DOCX files with macros (decision D6-5).
 */
public enum FileType {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp"),
    PDF("application/pdf", "pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_SIGNATURE = {'P', 'K', 0x03, 0x04};

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

    public boolean isImage() {
        return this == PNG || this == JPEG || this == WEBP;
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
        if (startsWith(content, "RIFF".getBytes(StandardCharsets.US_ASCII), 0)
                && startsWith(content, "WEBP".getBytes(StandardCharsets.US_ASCII), 8)) {
            return Optional.of(WEBP);
        }
        if (startsWith(content, PDF_SIGNATURE, 0)) {
            return Optional.of(PDF);
        }
        if (startsWith(content, ZIP_SIGNATURE, 0) && OfficeDocuments.isWordDocumentWithoutMacros(content)) {
            return Optional.of(DOCX);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, byte[] prefix, int offset) {
        return content != null
                && content.length >= offset + prefix.length
                && Arrays.equals(content, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}

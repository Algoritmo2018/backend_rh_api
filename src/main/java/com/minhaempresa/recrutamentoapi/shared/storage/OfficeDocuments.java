package com.minhaempresa.recrutamentoapi.shared.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Recognizes a Word document (DOCX) from its ZIP structure. Only the central directory and the small
 * {@code [Content_Types].xml} are read (never the whole archive), so a "zip bomb" is not decompressed.
 */
final class OfficeDocuments {

    private static final int MAX_ENTRIES = 2_000;
    private static final int MAX_CONTENT_TYPES_BYTES = 256 * 1024;

    private OfficeDocuments() {}

    /**
     * {@code true} for a DOCX: has {@code [Content_Types].xml} declaring the Word main document and
     * {@code word/document.xml}, and no macros ({@code vbaProject.bin} or a macro-enabled type).
     */
    static boolean isWordDocumentWithoutMacros(byte[] content) {
        Path temp = null;
        try {
            temp = Files.createTempFile("upload-", ".zip");
            Files.write(temp, content);
            try (ZipFile zip = new ZipFile(temp.toFile())) {
                if (zip.size() > MAX_ENTRIES) {
                    return false;
                }
                ZipEntry contentTypes = zip.getEntry("[Content_Types].xml");
                if (contentTypes == null || zip.getEntry("word/document.xml") == null) {
                    return false;
                }
                boolean macros = zip.stream().anyMatch(e -> e.getName().toLowerCase(Locale.ROOT).endsWith("vbaproject.bin"));
                if (macros) {
                    return false;
                }
                String types;
                try (InputStream in = zip.getInputStream(contentTypes)) {
                    types = new String(in.readNBytes(MAX_CONTENT_TYPES_BYTES), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                }
                return types.contains("wordprocessingml.document.main+xml") && !types.contains("macroenabled");
            }
        } catch (IOException e) {
            // Not a readable ZIP archive
            return false;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }
}

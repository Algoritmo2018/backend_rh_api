package com.minhaempresa.recrutamentoapi.shared.web;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * A stored file served by the API: {@code Content-Disposition} attachment (or inline for PDF/images), {@code nosniff},
 * {@code CSP: sandbox} (the page that shows it cannot run scripts) and {@code no-store}.
 */
public final class FileResponses {

    private FileResponses() {}

    public static ResponseEntity<Resource> of(Resource content, String contentType, String fileName, long sizeBytes, boolean inline) {
        ContentDisposition disposition = ContentDisposition.builder(inline ? "inline" : "attachment")
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(sizeBytes)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .body(content);
    }
}

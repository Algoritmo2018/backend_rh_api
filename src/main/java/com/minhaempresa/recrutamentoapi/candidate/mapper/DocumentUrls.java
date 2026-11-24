package com.minhaempresa.recrutamentoapi.candidate.mapper;

import java.util.UUID;

/** API paths of document files (relative: the frontend prefixes the API base URL and sends the token). */
public final class DocumentUrls {

    private DocumentUrls() {}

    public static String file(UUID documentId) {
        return documentId == null ? null : "/api/v1/documents/" + documentId + "/file";
    }

    /** Opened in the browser (photo as avatar, PDF viewer). */
    public static String inline(UUID documentId) {
        return documentId == null ? null : file(documentId) + "?inline=true";
    }
}

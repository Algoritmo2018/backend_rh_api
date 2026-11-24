package com.minhaempresa.recrutamentoapi.candidate.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;
import com.minhaempresa.recrutamentoapi.shared.storage.FileType;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Kind of candidate document (frontend {@code DocumentType}), with its upload rules: accepted formats,
 * maximum size and, for identity documents, the format of the number ({@code reference}).
 */
public enum DocumentType implements WireEnum {
    CV("cv", Limits.CV_BYTES, null),
    BI("bi", Limits.DOCUMENT_BYTES, "^\\d{9}[A-Z]{2}\\d{3}$"),
    NIF("nif", Limits.DOCUMENT_BYTES, "^[0-9A-Z]{9,14}$"),
    INSS("inss", Limits.DOCUMENT_BYTES, "^\\d{6,12}$"),
    CRIMINAL_RECORD("criminal_record", Limits.DOCUMENT_BYTES, null),
    MEDICAL_CERTIFICATE("medical_certificate", Limits.DOCUMENT_BYTES, null),
    CERTIFICATE("certificate", Limits.DOCUMENT_BYTES, null),
    PHOTO("photo", Limits.PHOTO_BYTES, null),
    OTHER("other", Limits.DOCUMENT_BYTES, null);

    /** PDF, Word (DOCX without macros) or an image; a photo is only PNG or JPEG. */
    private static final Set<FileType> DOCUMENT_FORMATS = Set.of(FileType.PDF, FileType.DOCX, FileType.PNG, FileType.JPEG);
    private static final Set<FileType> PHOTO_FORMATS = Set.of(FileType.PNG, FileType.JPEG);

    private final String value;
    private final long maxBytes;
    private final Pattern referencePattern;

    DocumentType(String value, long maxBytes, String referencePattern) {
        this.value = value;
        this.maxBytes = maxBytes;
        this.referencePattern = referencePattern == null ? null : Pattern.compile(referencePattern);
    }

    @Override
    public String getValue() {
        return value;
    }

    /** CV 5 MB, photo 2 MB, other documents 10 MB (frontend limits). */
    public long maxBytes() {
        return maxBytes;
    }

    public Set<FileType> acceptedFormats() {
        return this == PHOTO ? PHOTO_FORMATS : DOCUMENT_FORMATS;
    }

    /** BI, NIF and INSS need their number, in this format (upper case, no spaces). */
    public boolean requiresReference() {
        return referencePattern != null;
    }

    public boolean isValidReference(String reference) {
        return referencePattern == null || (reference != null && referencePattern.matcher(reference).matches());
    }

    /**
     * Identity documents and sensitive personal data (criminal record, health): only the candidate,
     * administrators and recruiters may open them (decision D6-4).
     */
    public boolean isSensitive() {
        return this == BI || this == NIF || this == INSS || this == CRIMINAL_RECORD || this == MEDICAL_CERTIFICATE;
    }

    @JsonCreator
    public static DocumentType fromValue(String value) {
        return WireEnums.fromValue(DocumentType.class, value);
    }

    private static final class Limits {
        static final long CV_BYTES = 5L * 1024 * 1024;
        static final long PHOTO_BYTES = 2L * 1024 * 1024;
        static final long DOCUMENT_BYTES = 10L * 1024 * 1024;
    }
}

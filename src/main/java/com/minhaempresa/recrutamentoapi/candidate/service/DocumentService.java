package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.candidate.dto.request.DocumentReviewRequest;
import com.minhaempresa.recrutamentoapi.candidate.dto.request.DocumentUploadRequest;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.DocumentFile;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.DocumentResponse;
import com.minhaempresa.recrutamentoapi.candidate.entity.Candidate;
import com.minhaempresa.recrutamentoapi.candidate.entity.CandidateDocument;
import com.minhaempresa.recrutamentoapi.candidate.entity.DocumentAccess;
import com.minhaempresa.recrutamentoapi.candidate.entity.DocumentStatus;
import com.minhaempresa.recrutamentoapi.candidate.entity.DocumentType;
import com.minhaempresa.recrutamentoapi.candidate.mapper.CandidateMapper;
import com.minhaempresa.recrutamentoapi.candidate.repository.CandidateRepository;
import com.minhaempresa.recrutamentoapi.candidate.repository.DocumentAccessRepository;
import com.minhaempresa.recrutamentoapi.candidate.repository.DocumentRepository;
import com.minhaempresa.recrutamentoapi.shared.config.AppProperties;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.ForbiddenException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.storage.FileScanner;
import com.minhaempresa.recrutamentoapi.shared.storage.FileType;
import com.minhaempresa.recrutamentoapi.shared.storage.StorageCleanup;
import com.minhaempresa.recrutamentoapi.shared.storage.StorageService;
import com.minhaempresa.recrutamentoapi.shared.storage.StoredFile;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Documents of the candidates (CV, identity documents, certificates, photo).
 *
 * <ul>
 *   <li>Upload by the candidate only. The type is detected from the content (PDF, DOCX without macros,
 *       PNG, JPEG; a photo only PNG/JPEG), never from the name or {@code Content-Type}; the storage key is
 *       generated; the original name is only metadata.
 *   <li>Files are always read through {@link #open}: the owner, administrators and recruiters; an
 *       interviewer only the CV of a candidate assigned to them (decision D6-1). Sensitive documents (BI,
 *       NIF, INSS, criminal record, medical certificate) are never opened by interviewers (D6-4). Every
 *       opening is recorded (who, when, view/download), without the IP (D6-13).
 *   <li>A deleted document's file is removed from the bucket only after the commit; a file uploaded in a
 *       transaction that fails is removed too.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository repository;
    private final DocumentAccessRepository accessRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateService candidateService;
    private final CandidateAccessPolicy accessPolicy;
    private final CandidateMapper mapper;
    private final StorageService storage;
    private final StorageCleanup storageCleanup;
    private final FileScanner fileScanner;
    private final AppProperties appProperties;
    private final Clock clock;

    // =====================================================================================
    // Candidate (own documents)
    // =====================================================================================

    @Transactional
    public List<DocumentResponse> listMine(AuthenticatedUser actor) {
        return list(candidateService.ownCandidateId(actor));
    }

    /**
     * Stores a new document of the own profile. A CV becomes the current CV and a photo the profile
     * photo; older ones stay in the list.
     */
    @Transactional
    public DocumentResponse uploadMine(AuthenticatedUser actor, DocumentUploadRequest request, String originalName, byte[] content) {
        UUID candidateId = candidateService.ownCandidateId(actor);
        DocumentType type = request.type();
        if (type == DocumentType.CLIENT_CV) {
            // Only a recruiter uploads the CV for the client, for one application (stage 8c)
            throw fieldError("type", "document.type.not-allowed");
        }
        if (content == null || content.length == 0) {
            throw fieldError("file", "document.file.required");
        }
        if (content.length > type.maxBytes()) {
            long megabytes = type.maxBytes() / (1024 * 1024);
            throw new BusinessException("document.too-large", List.of(new FieldError("file", "document.too-large", List.of(megabytes))), megabytes);
        }
        FileType fileType = FileType.detect(content)
                .filter(type.acceptedFormats()::contains)
                .orElseThrow(() -> fieldError("file", type == DocumentType.PHOTO ? "document.photo.invalid-type" : "document.invalid-type"));
        String reference = null;
        if (type.requiresReference()) {
            reference = request.reference() == null ? "" : request.reference().replaceAll("\\s", "").toUpperCase(Locale.ROOT);
            if (reference.isEmpty()) {
                throw fieldError("reference", "document.reference.required");
            }
            if (!type.isValidReference(reference)) {
                throw fieldError("reference", "document.reference.invalid." + type.getValue());
            }
        }
        if (request.expiresAt() != null && request.expiresAt().isBefore(today())) {
            throw fieldError("expiresAt", "document.expires.past");
        }
        fileScanner.scan(content);

        StoredFile stored = storage.store(type == DocumentType.PHOTO ? "photos" : "documents", fileType, content);
        storageCleanup.deleteIfRolledBack(stored.key());
        CandidateDocument document = repository.save(new CandidateDocument(candidateId, type, safeFileName(originalName, fileType), fileType.mimeType(),
                stored.sizeBytes(), stored.key(), stored.sha256(), reference, request.expiresAt(), clock.instant()));
        Candidate candidate = findCandidate(candidateId);
        if (type == DocumentType.CV) {
            candidate.setCvDocumentId(document.getId());
        } else if (type == DocumentType.PHOTO) {
            candidate.setPhotoDocumentId(document.getId());
        }
        repository.flush();
        return mapper.toDocumentResponse(document);
    }

    /**
     * Checks the CV sent with an application (5 MB; PDF, DOCX, PNG, JPEG by content) before anything is
     * stored. Errors are on {@code field}.
     */
    public void checkApplicationCv(byte[] content, String field) {
        detectCv(content, field);
    }

    /**
     * Stores the CV of an application as a document of the candidate (stage 8, D8-2), after the virus scan.
     * It becomes the profile's current CV only with {@code makeCurrent} (a profile created by this
     * application); an existing profile is never changed by an application.
     *
     * @return the id of the new document
     */
    @Transactional
    public UUID storeApplicationCv(UUID candidateId, String originalName, byte[] content, boolean makeCurrent, String field) {
        FileType fileType = detectCv(content, field);
        fileScanner.scan(content);
        StoredFile stored = storage.store("documents", fileType, content);
        storageCleanup.deleteIfRolledBack(stored.key());
        CandidateDocument document = repository.save(new CandidateDocument(candidateId, DocumentType.CV, safeFileName(originalName, fileType), fileType.mimeType(),
                stored.sizeBytes(), stored.key(), stored.sha256(), null, null, clock.instant()));
        if (makeCurrent) {
            findCandidate(candidateId).setCvDocumentId(document.getId());
        }
        repository.flush();
        return document.getId();
    }

    /**
     * Stores the "CV para o cliente" of an application (stage 8c): PDF only, 5 MB, checked by content, virus scan.
     * A document of the candidate of type {@code client_cv}; never the profile's current CV.
     *
     * @return the id of the new document
     */
    @Transactional
    public UUID storeClientCv(UUID candidateId, String originalName, byte[] content, String field) {
        DocumentType type = DocumentType.CLIENT_CV;
        if (content == null || content.length == 0) {
            throw fieldError(field, "document.file.required");
        }
        if (content.length > type.maxBytes()) {
            long megabytes = type.maxBytes() / (1024 * 1024);
            throw new BusinessException("document.too-large", List.of(new FieldError(field, "document.too-large", List.of(megabytes))), megabytes);
        }
        FileType fileType = FileType.detect(content).filter(type.acceptedFormats()::contains)
                .orElseThrow(() -> fieldError(field, "document.client-cv.invalid-type"));
        findCandidate(candidateId);
        fileScanner.scan(content);
        StoredFile stored = storage.store("documents", fileType, content);
        storageCleanup.deleteIfRolledBack(stored.key());
        CandidateDocument document = repository.save(new CandidateDocument(candidateId, type, safeFileName(originalName, fileType), fileType.mimeType(),
                stored.sizeBytes(), stored.key(), stored.sha256(), null, null, clock.instant()));
        repository.flush();
        return document.getId();
    }

    /** Deletes a "CV para o cliente" (replaced or removed by the recruiter); the file leaves after the commit. */
    @Transactional
    public void deleteClientCv(UUID documentId) {
        repository.findById(documentId).filter(d -> d.getType() == DocumentType.CLIENT_CV).ifPresent(document -> {
            repository.delete(document);
            storageCleanup.deleteAfterCommit(List.of(document.getStorageKey()));
            repository.flush();
        });
    }

    /**
     * The "CV para o cliente" opened by a client user (stage 8c). The caller (applications module) has already
     * checked the isolation (the application is shared with this user's company); here only a {@code client_cv}
     * is ever served (never the original CV nor another document) and the opening is logged.
     */
    @Transactional
    public DocumentFile openClientCv(UUID documentId, boolean inline, UUID viewerId) {
        CandidateDocument document = repository.findById(documentId).filter(d -> d.getType() == DocumentType.CLIENT_CV)
                .orElseThrow(DocumentService::notFound);
        accessRepository.save(new DocumentAccess(document, viewerId, inline ? DocumentAccess.Action.VIEW : DocumentAccess.Action.DOWNLOAD, clock.instant()));
        return new DocumentFile(storage.load(document.getStorageKey()), document.getMimeType(), document.getFileName(), document.getSizeBytes(), inline);
    }

    private static FileType detectCv(byte[] content, String field) {
        DocumentType type = DocumentType.CV;
        if (content == null || content.length == 0) {
            throw fieldError(field, "document.file.required");
        }
        if (content.length > type.maxBytes()) {
            long megabytes = type.maxBytes() / (1024 * 1024);
            throw new BusinessException("document.too-large", List.of(new FieldError(field, "document.too-large", List.of(megabytes))), megabytes);
        }
        return FileType.detect(content)
                .filter(type.acceptedFormats()::contains)
                .orElseThrow(() -> fieldError(field, "document.invalid-type"));
    }

    /** Removes an own document (not an approved one); its file leaves the bucket after the commit. */
    @Transactional
    public void deleteMine(AuthenticatedUser actor, UUID id) {
        UUID candidateId = candidateService.ownCandidateId(actor);
        CandidateDocument document = repository.findByIdAndCandidateId(id, candidateId).orElseThrow(DocumentService::notFound);
        if (document.getStatus() == DocumentStatus.APPROVED) {
            throw new BusinessException("document.approved-locked");
        }
        Candidate candidate = findCandidate(candidateId);
        if (document.getId().equals(candidate.getCvDocumentId())) {
            candidate.setCvDocumentId(newestOther(document).orElse(null));
        }
        if (document.getId().equals(candidate.getPhotoDocumentId())) {
            candidate.setPhotoDocumentId(newestOther(document).orElse(null));
        }
        repository.delete(document);
        storageCleanup.deleteAfterCommit(List.of(document.getStorageKey()));
        repository.flush();
    }

    // =====================================================================================
    // Staff
    // =====================================================================================

    /** Every document of a candidate (administrators and recruiters). */
    @Transactional(readOnly = true)
    public List<DocumentResponse> listForCandidate(UUID candidateId) {
        findCandidate(candidateId);
        return list(candidateId);
    }

    /** Approves or rejects a document; the reason of a rejection is shown to the candidate. */
    @Transactional
    public DocumentResponse review(UUID id, DocumentReviewRequest request, AuthenticatedUser reviewer) {
        if (request.status() == DocumentStatus.SUBMITTED) {
            throw fieldError("status", "document.review.status");
        }
        CandidateDocument document = repository.findById(id).orElseThrow(DocumentService::notFound);
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        document.review(request.status(), note, reviewer.id(), clock.instant());
        repository.flush();
        return mapper.toDocumentResponse(document);
    }

    // =====================================================================================
    // File access (every role, checked here)
    // =====================================================================================

    /**
     * The file of a document, after checking who asks; every successful opening is recorded. A
     * candidate asking for another candidate's document gets 404 (same as a missing id).
     *
     * @param inline open in the browser (only PDF and images; Word files are always downloaded)
     */
    @Transactional
    public DocumentFile open(UUID id, boolean inline, AuthenticatedUser viewer) {
        CandidateDocument document = repository.findById(id).orElseThrow(DocumentService::notFound);
        requireAccess(document, viewer);
        boolean showInline = inline && !FileType.DOCX.mimeType().equals(document.getMimeType());
        accessRepository.save(new DocumentAccess(document, viewer.id(), showInline ? DocumentAccess.Action.VIEW : DocumentAccess.Action.DOWNLOAD, clock.instant()));
        return new DocumentFile(storage.load(document.getStorageKey()), document.getMimeType(), document.getFileName(), document.getSizeBytes(), showInline);
    }

    private void requireAccess(CandidateDocument document, AuthenticatedUser viewer) {
        if (viewer.hasRole(UserRole.ADMIN.name()) || viewer.hasRole(UserRole.RECRUITER.name())) {
            return;
        }
        if (viewer.hasRole(UserRole.CANDIDATE.name())) {
            if (!document.getCandidateId().equals(candidateService.ownCandidateId(viewer))) {
                throw notFound();
            }
            return;
        }
        if (viewer.hasRole(UserRole.INTERVIEWER.name())) {
            // Only the CV, and only of a candidate assigned to them (conscious choice: it may contain contacts)
            if (!accessPolicy.interviewerMaySee(viewer.id(), document.getCandidateId())) {
                throw new ForbiddenException("candidate.not-assigned");
            }
            if (document.getType() != DocumentType.CV) {
                throw new ForbiddenException("document.forbidden");
            }
            return;
        }
        throw new ForbiddenException();
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private List<DocumentResponse> list(UUID candidateId) {
        return repository.findByCandidateIdOrderByUploadedAtDescIdDesc(candidateId).stream().map(mapper::toDocumentResponse).toList();
    }

    private java.util.Optional<UUID> newestOther(CandidateDocument document) {
        return repository.findFirstByCandidateIdAndTypeAndIdNotOrderByUploadedAtDescIdDesc(document.getCandidateId(), document.getType(), document.getId())
                .map(CandidateDocument::getId);
    }

    /**
     * Original base name without folders, control characters nor quotes, at most 120 characters, with
     * the extension of the DETECTED type ({@code cv.exe} that is a PDF becomes {@code cv.pdf}).
     */
    static String safeFileName(String originalName, FileType type) {
        String name = originalName == null ? "" : originalName;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"';<>|*?:]", "").trim();
        int dot = name.lastIndexOf('.');
        String base = (dot > 0 ? name.substring(0, dot) : name).trim();
        if (base.isEmpty() || base.chars().allMatch(c -> c == '.')) {
            base = "documento";
        }
        if (base.length() > 120) {
            base = base.substring(0, 120).trim();
        }
        return base + "." + type.extension();
    }

    private Candidate findCandidate(UUID id) {
        return candidateRepository.findById(id).orElseThrow(() -> new NotFoundException("candidate.not-found"));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(appProperties.defaults().timeZone()));
    }

    private static NotFoundException notFound() {
        return new NotFoundException("document.not-found");
    }

    private static BusinessException fieldError(String field, String key) {
        return new BusinessException(key, List.of(new FieldError(field, key)));
    }
}

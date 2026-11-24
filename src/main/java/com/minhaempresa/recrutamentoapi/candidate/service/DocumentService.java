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

package com.minhaempresa.recrutamentoapi.candidate.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One opening or download of a document (Lei n.º 22/11 accountability). Immutable; no IP address
 * (decision D6-13). Survives the deletion of the document.
 */
@Getter
@Entity
@Table(name = "document_access_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentAccess extends BaseEntity {

    public enum Action {
        /** Opened in the browser ({@code inline}). */
        VIEW,
        /** Saved as a file ({@code attachment}). */
        DOWNLOAD
    }

    @Column(name = "document_id", updatable = false)
    private UUID documentId;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20, updatable = false)
    private DocumentType documentType;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 10, updatable = false)
    private Action action;

    @Column(name = "accessed_at", nullable = false, updatable = false)
    private Instant accessedAt;

    public DocumentAccess(CandidateDocument document, UUID userId, Action action, Instant accessedAt) {
        this.documentId = document.getId();
        this.candidateId = document.getCandidateId();
        this.documentType = document.getType();
        this.userId = userId;
        this.action = action;
        this.accessedAt = accessedAt;
    }
}

package com.minhaempresa.recrutamentoapi.application.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A comment in the shortlist of an application (frontend {@code ClientComment}): written by FEH OIRAM (stage 8c) or
 * by the client (stage 13). Deleted with the candidate's personal data (D8-8). Never changed after it is written.
 */
@Getter
@Entity
@Table(name = "client_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientComment extends BaseEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    /** {@code null} once the author's account is deleted. */
    @Column(name = "author_id", updatable = false)
    private UUID authorId;

    @Column(name = "from_client", nullable = false, updatable = false)
    private boolean fromClient;

    @Column(name = "text", nullable = false, length = 1000, updatable = false)
    private String text;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ClientComment(UUID applicationId, UUID authorId, boolean fromClient, String text, Instant createdAt) {
        this.applicationId = applicationId;
        this.authorId = authorId;
        this.fromClient = fromClient;
        this.text = text;
        this.createdAt = createdAt;
    }
}

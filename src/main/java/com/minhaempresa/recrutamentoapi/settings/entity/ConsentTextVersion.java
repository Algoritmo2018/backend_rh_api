package com.minhaempresa.recrutamentoapi.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Exact consent texts of one version (Lei n.º 22/11: it must be possible to prove what each person
 * accepted). Immutable: a change of text creates a new version.
 */
@Getter
@Entity
@Table(name = "consent_text_versions")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentTextVersion {

    /** {@code 2026.1}, {@code 2026.2}… */
    @Id
    @Column(name = "version", nullable = false, length = 20, updatable = false)
    private String version;

    @Column(name = "job_text", nullable = false, length = 1500, updatable = false)
    private String jobText;

    @Column(name = "talent_pool_text", nullable = false, length = 1500, updatable = false)
    private String talentPoolText;

    /** The job-application text covers sharing with the client company (stage 8c): only then may applications be shared. */
    @Column(name = "includes_client_sharing", nullable = false, updatable = false)
    private boolean includesClientSharing;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    public ConsentTextVersion(String version, String jobText, String talentPoolText, boolean includesClientSharing) {
        this.version = version;
        this.jobText = jobText;
        this.talentPoolText = talentPoolText;
        this.includesClientSharing = includesClientSharing;
    }
}

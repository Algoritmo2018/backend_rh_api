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
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * The single row of company settings. The database guarantees there is only one
 * ({@code id SMALLINT PRIMARY KEY CHECK (id = 1)}); the row comes from the seed migration and is only
 * ever updated.
 */
@Getter
@Setter
@Entity
@Table(name = "company_settings")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanySettings {

    public static final short SINGLETON_ID = 1;

    @Id
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false, updatable = false)
    private Short id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "legal_name", nullable = false, length = 160)
    private String legalName;

    /** Angolan tax identification number. */
    @Column(name = "nif", nullable = false, length = 14)
    private String nif;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Column(name = "phone", nullable = false, length = 16)
    private String phone;

    @Column(name = "address", nullable = false, length = 200)
    private String address;

    @Column(name = "website", nullable = false, length = 200)
    private String website;

    /** Storage key of the logo ({@code null} = no logo). */
    @Column(name = "logo_key", length = 200)
    private String logoKey;

    @Column(name = "logo_content_type", length = 40)
    private String logoContentType;

    /** Short hash of the logo content, used as cache-busting version in the URL and as ETag. */
    @Column(name = "logo_hash", length = 16)
    private String logoHash;

    @Column(name = "logo_original_name", length = 255)
    private String logoOriginalName;

    @Column(name = "data_retention_months", nullable = false)
    private int dataRetentionMonths;

    @Column(name = "consent_text_version", nullable = false, length = 20)
    private String consentTextVersion;

    @Column(name = "consent_job_text", nullable = false, length = 1500)
    private String consentJobText;

    @Column(name = "consent_talent_pool_text", nullable = false, length = 1500)
    private String consentTalentPoolText;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;
}

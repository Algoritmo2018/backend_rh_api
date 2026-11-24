package com.minhaempresa.recrutamentoapi.candidate.entity;

import com.minhaempresa.recrutamentoapi.shared.domain.CandidateSource;
import com.minhaempresa.recrutamentoapi.shared.domain.Money;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Candidate profile (frontend {@code Candidate}). May exist without an account ({@code userId} null):
 * applications identify it by email. Only the owner ({@code /me}) and staff change it; an application
 * never does. {@code gender}, {@code birthDate}, {@code nationality} and the photo are never used in the
 * match score or the talent search.
 */
@Getter
@Setter
@Entity
@Table(name = "candidates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Candidate extends UserAuditableEntity {

    /** Portal account, linked after the email was confirmed. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    /** Lower case. Changes only through a confirmed email change (never by {@code PATCH}). */
    @Setter(AccessLevel.NONE)
    @Column(name = "email", nullable = false, length = 160)
    private String email;

    /** E.164. */
    @Column(name = "phone", nullable = false, length = 16)
    private String phone;

    @Column(name = "whatsapp", length = 16)
    private String whatsapp;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", columnDefinition = "varchar(1)")
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "nationality", nullable = false, length = 40)
    private String nationality;

    /** {@code false} = expatriate (angolanization report). */
    @Column(name = "is_angolan", nullable = false)
    private boolean angolan = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "province", length = 30)
    private Province province;

    @Column(name = "city", nullable = false, length = 60)
    private String city = "";

    @Column(name = "headline", nullable = false, length = 80)
    private String headline = "";

    @Column(name = "summary", length = 600)
    private String summary;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "skills", nullable = false, columnDefinition = "text[]")
    private List<String> skills = new ArrayList<>();

    /** Categories {@code A}–{@code E}. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "driver_licenses", nullable = false, columnDefinition = "text[]")
    private List<String> driverLicenses = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false, length = 10)
    private Availability availability = Availability.NEGOTIABLE;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "salary_expectation_amount", precision = 19, scale = 2)),
        @AttributeOverride(name = "currency", column = @Column(name = "salary_expectation_currency", length = 3))
    })
    private Money salaryExpectation;

    /** Current CV (documents, stage 6b). */
    @Column(name = "cv_document_id")
    private UUID cvDocumentId;

    @Column(name = "total_experience_years", nullable = false)
    private int totalExperienceYears;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private CandidateSource source;

    /** Mirror of an active talent-pool consent: changed only by the consents service. */
    @Column(name = "in_talent_pool", nullable = false)
    private boolean inTalentPool;

    /** Text indexed for the talent search (rebuilt on every save). */
    @Column(name = "search_text", nullable = false, columnDefinition = "text")
    private String searchText = "";

    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "candidate_tags", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "tag_id", nullable = false)
    private Set<UUID> tagIds = new LinkedHashSet<>();

    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<WorkExperience> experience = new ArrayList<>();

    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<Education> education = new ArrayList<>();

    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<CandidateLanguage> languages = new ArrayList<>();

    public Candidate(String fullName, String email, String phone, String nationality, CandidateSource source) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.nationality = nationality;
        this.source = source;
    }

    public WorkExperience addExperience() {
        WorkExperience item = new WorkExperience(this);
        experience.add(item);
        return item;
    }

    public Education addEducation() {
        Education item = new Education(this);
        education.add(item);
        return item;
    }

    public CandidateLanguage addLanguage(String language, String key, LanguageLevel level) {
        CandidateLanguage item = new CandidateLanguage(this, language, key, level);
        languages.add(item);
        return item;
    }
}

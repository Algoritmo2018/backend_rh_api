package com.minhaempresa.recrutamentoapi.job.entity;

import com.minhaempresa.recrutamentoapi.shared.domain.JobArea;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
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
import jakarta.persistence.Version;
import java.time.Instant;
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
 * Job (vaga). Never deleted: {@code CLOSED} is the archive. The description is HTML already sanitized
 * (allow-list) before it reaches this entity. Concurrent edits are detected with {@link #version}.
 */
@Getter
@Setter
@Entity
@Table(name = "jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job extends UserAuditableEntity {

    /** Public URL {@code /vagas/{slug}}; regenerated only while the job was never published. */
    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    @Column(name = "title", nullable = false, length = 90)
    private String title;

    /** Client company; can only change while the job is a draft that did not come from a request. */
    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    /** The public page shows "Empresa confidencial" instead of the client name. */
    @Column(name = "is_confidential", nullable = false)
    private boolean confidential;

    @Enumerated(EnumType.STRING)
    @Column(name = "area", nullable = false, length = 20)
    private JobArea area;

    @Enumerated(EnumType.STRING)
    @Column(name = "province", nullable = false, length = 30)
    private Province province;

    @Column(name = "city", nullable = false, length = 60)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", nullable = false, length = 10)
    private WorkMode workMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 20)
    private ContractType contractType;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", nullable = false, length = 10)
    private ExperienceLevel experienceLevel;

    @Column(name = "min_experience_years", nullable = false)
    private int minExperienceYears;

    @Column(name = "vacancies", nullable = false)
    private int vacancies;

    /** Sanitized HTML (p, strong, em, ul, li, br). */
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    /** Plain text of {@link #description} (full-text search). */
    @Column(name = "description_text", nullable = false, columnDefinition = "text")
    private String descriptionText;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "responsibilities", nullable = false, columnDefinition = "text[]")
    private List<String> responsibilities = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "benefits", nullable = false, columnDefinition = "text[]")
    private List<String> benefits = new ArrayList<>();

    @Embedded
    private SalaryRange salary;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private JobStatus status = JobStatus.DRAFT;

    @Column(name = "is_featured", nullable = false)
    private boolean featured;

    /** Position for expatriates (angolanization report). */
    @Column(name = "is_expatriate", nullable = false)
    private boolean expatriate;

    @Column(name = "recruiter_id", nullable = false)
    private UUID recruiterId;

    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "job_interviewers", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "user_id", nullable = false)
    private Set<UUID> interviewerIds = new LinkedHashSet<>();

    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "job_tags", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "tag_id", nullable = false)
    private Set<UUID> tagIds = new LinkedHashSet<>();

    /** Last day to apply (inclusive, Angola time). */
    @Column(name = "deadline")
    private LocalDate deadline;

    @Setter(AccessLevel.NONE)
    @Column(name = "published_at")
    private Instant publishedAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "closed_at")
    private Instant closedAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "job_request_id", updatable = false)
    private UUID jobRequestId;

    @Setter(AccessLevel.NONE)
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<JobRequirement> requirements = new ArrayList<>();

    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ScreeningQuestion> screeningQuestions = new ArrayList<>();

    public Job(UUID clientId, UUID jobRequestId) {
        this.clientId = clientId;
        this.jobRequestId = jobRequestId;
    }

    public boolean wasPublished() {
        return publishedAt != null;
    }

    /**
     * Moves to {@code target} (the caller checked {@link JobStatus#canChangeTo}). Returns {@code true}
     * when this is the first publication.
     */
    public boolean changeStatus(JobStatus target, Instant now) {
        boolean firstPublication = target == JobStatus.OPEN && publishedAt == null;
        if (firstPublication) {
            publishedAt = now;
        }
        if (target == JobStatus.CLOSED && status != JobStatus.CLOSED) {
            closedAt = now;
        }
        status = target;
        return firstPublication;
    }

    public JobRequirement addRequirement() {
        JobRequirement requirement = new JobRequirement(this);
        requirements.add(requirement);
        return requirement;
    }

    public ScreeningQuestion addScreeningQuestion() {
        ScreeningQuestion question = new ScreeningQuestion(this);
        screeningQuestions.add(question);
        return question;
    }
}

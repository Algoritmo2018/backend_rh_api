package com.minhaempresa.recrutamentoapi.application.entity;

import com.minhaempresa.recrutamentoapi.shared.domain.CandidateSource;
import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
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

/**
 * An application of a candidate to a job (frontend {@code Application}). One per (job, candidate).
 *
 * <p>It keeps its own copy of what was sent (name, email, phone, province, CV, quick profile, answers): later
 * changes to the profile or to the account email never change it (D8-1). The score and the requirement matches
 * are computed once, when applying, and never recalculated (D8-3). {@code eliminated} (an eliminatory question
 * answered differently from the expected answer) is never shown to the candidate.
 */
@Getter
@Setter
@Entity
@Table(name = "applications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends UserAuditableEntity {

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    private PipelineStageKey stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ApplicationStatus status = ApplicationStatus.ACTIVE;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    /** Lower case. */
    @Column(name = "email", nullable = false, length = 160)
    private String email;

    /** E.164. */
    @Column(name = "phone", nullable = false, length = 16)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "province", length = 30)
    private Province province;

    @Column(name = "cv_document_id")
    private UUID cvDocumentId;

    @Embedded
    private QuickProfile quickProfile;

    /**
     * Match score 0–100; {@code null} = nothing structured to evaluate (anonymous application with a CV only).
     * 0 = evaluated and no requirement met.
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "score", updatable = false)
    private Integer score;

    @Setter(AccessLevel.NONE)
    @Column(name = "eliminated", nullable = false, updatable = false)
    private boolean eliminated;

    /** Recruiter's stars, 0–5. */
    @Column(name = "rating", nullable = false)
    private int rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20, updatable = false)
    private CandidateSource source;

    @Column(name = "rejection_reason_id")
    private UUID rejectionReasonId;

    @Column(name = "rejection_note", length = 500)
    private String rejectionNote;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejected_by")
    private UUID rejectedBy;

    @Column(name = "shared_with_client", nullable = false)
    private boolean sharedWithClient;

    @Column(name = "shared_at")
    private Instant sharedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_decision", length = 10)
    private ClientDecision clientDecision;

    /** Visible to the client (stage 8c). */
    @Column(name = "recruiter_summary", columnDefinition = "text")
    private String recruiterSummary;

    /** "CV para o cliente" (type {@code client_cv}): the only file the client may open (stage 8c). */
    @Column(name = "client_cv_document_id")
    private UUID clientCvDocumentId;

    /**
     * Made with the candidate's stored profile (logged-in candidate, added from the talent pool): only then does
     * the client see the stored profile; an anonymous application shows only what it sent (stage 8c).
     */
    @Column(name = "from_profile", nullable = false, updatable = false)
    private boolean fromProfile;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    @Column(name = "stage_entered_at", nullable = false)
    private Instant stageEnteredAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "application_answers", joinColumns = @JoinColumn(name = "application_id"))
    @OrderColumn(name = "position")
    private List<ApplicationAnswer> answers = new ArrayList<>();

    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "application_requirement_matches", joinColumns = @JoinColumn(name = "application_id"))
    @OrderColumn(name = "position")
    private List<RequirementMatchEntry> matches = new ArrayList<>();

    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "application_tags", joinColumns = @JoinColumn(name = "application_id"))
    @Column(name = "tag_id", nullable = false)
    private Set<UUID> tagIds = new LinkedHashSet<>();

    public Application(UUID jobId, UUID candidateId, PipelineStageKey stage, CandidateSource source, Integer score, boolean eliminated, Instant appliedAt) {
        this.jobId = jobId;
        this.candidateId = candidateId;
        this.stage = stage;
        this.source = source;
        this.score = score;
        this.eliminated = eliminated;
        this.appliedAt = appliedAt;
        this.stageEnteredAt = appliedAt;
    }

    /** Still in the process (in progress or hired): may be moved and rejected. */
    public boolean inProcess() {
        return status == ApplicationStatus.ACTIVE || status == ApplicationStatus.HIRED;
    }

    /**
     * Moves to another stage (frontend {@code moveApplication}): the final stage makes it hired (D8-6), any other
     * stage in progress. Sharing on entering the client interview is decided by the service (consent check).
     */
    public void moveTo(PipelineStageKey target, Instant now) {
        stage = target;
        stageEnteredAt = now;
        status = target == PipelineStageKey.HIRED ? ApplicationStatus.HIRED : ApplicationStatus.ACTIVE;
    }

    /** Shared with the client company (shortlist), awaiting its decision or already approved. */
    public void shareWithClient(ClientDecision decision, Instant now) {
        sharedWithClient = true;
        sharedAt = now;
        clientDecision = decision;
    }

    /** The client may see it: shared and still in the process (rejected or withdrawn ones disappear, stage 8c). */
    public boolean visibleToClient() {
        return sharedWithClient && inProcess();
    }

    public void reject(UUID reasonId, String note, UUID byUserId, Instant now) {
        status = ApplicationStatus.REJECTED;
        rejectionReasonId = reasonId;
        rejectionNote = note;
        rejectedAt = now;
        rejectedBy = byUserId;
    }

    /** Back to the pipeline, in the stage where it was (hired again when that stage is the final one). */
    public void restore() {
        status = stage == PipelineStageKey.HIRED ? ApplicationStatus.HIRED : ApplicationStatus.ACTIVE;
        rejectionReasonId = null;
        rejectionNote = null;
        rejectedAt = null;
        rejectedBy = null;
    }

    /**
     * Erasure of the candidate (Lei n.º 22/11, D8-8): the row stays for the statistics, every personal data of
     * the application goes. An application still in progress ends as withdrawn.
     */
    public void anonymize(String placeholderName) {
        if (status == ApplicationStatus.ACTIVE) {
            status = ApplicationStatus.WITHDRAWN;
        }
        fullName = placeholderName;
        email = "";
        phone = "";
        province = null;
        cvDocumentId = null;
        clientCvDocumentId = null;
        quickProfile = null;
        rejectionNote = null;
        recruiterSummary = null;
        answers.clear();
        matches.clear();
    }

    @Override
    public String toString() {
        return "Application(id=" + getId() + ", stage=" + stage + ", status=" + status + ")";
    }
}

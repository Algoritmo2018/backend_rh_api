package com.minhaempresa.recrutamentoapi.client.entity;

import com.minhaempresa.recrutamentoapi.shared.domain.JobArea;
import com.minhaempresa.recrutamentoapi.shared.domain.Money;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Job request made by a client in the client portal. FEH OIRAM rejects it (with a reason the client
 * sees) or converts it into a job (stage 5).
 */
@Getter
@Setter
@Entity
@Table(name = "job_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobRequest extends UserAuditableEntity {

    @Setter(AccessLevel.NONE)
    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    /** Client user who made the request ({@code null} if that user was deleted). */
    @Setter(AccessLevel.NONE)
    @Column(name = "requested_by_id", updatable = false)
    private UUID requestedById;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "area", nullable = false, length = 20)
    private JobArea area;

    @Enumerated(EnumType.STRING)
    @Column(name = "province", nullable = false, length = 30)
    private Province province;

    @Column(name = "vacancies", nullable = false)
    private int vacancies;

    @Column(name = "description", nullable = false, length = 3000)
    private String description;

    @Column(name = "requirements", nullable = false, length = 3000)
    private String requirements;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "salary_budget_amount", precision = 19, scale = 2)),
        @AttributeOverride(name = "currency", column = @Column(name = "salary_budget_currency", length = 3))
    })
    private Money salaryBudget;

    @Column(name = "desired_start_date")
    private LocalDate desiredStartDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency", nullable = false, length = 10)
    private JobRequestUrgency urgency;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private JobRequestStatus status = JobRequestStatus.PENDING;

    @Setter(AccessLevel.NONE)
    @Column(name = "job_id")
    private UUID jobId;

    @Setter(AccessLevel.NONE)
    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Setter(AccessLevel.NONE)
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public JobRequest(UUID clientId, UUID requestedById) {
        this.clientId = clientId;
        this.requestedById = requestedById;
    }

    public boolean isPending() {
        return status == JobRequestStatus.PENDING;
    }

    public void reject(String note, Instant now) {
        status = JobRequestStatus.REJECTED;
        reviewNote = note;
        reviewedAt = now;
    }

    public void convert(UUID createdJobId, Instant now) {
        status = JobRequestStatus.CONVERTED;
        jobId = createdJobId;
        reviewedAt = now;
    }
}

package com.minhaempresa.recrutamentoapi.candidate.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
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
import lombok.Setter;

/** Professional reference of a candidate (frontend {@code CandidateReference}), checked by recruiters. */
@Getter
@Setter
@Entity
@Table(name = "candidate_references")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateReference extends UserAuditableEntity {

    @Setter(AccessLevel.NONE)
    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    /** Application it was collected for (stage 8). */
    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "company", nullable = false, length = 80)
    private String company;

    @Column(name = "role", nullable = false, length = 80)
    private String role;

    @Column(name = "relationship", nullable = false, length = 60)
    private String relationship;

    /** E.164. */
    @Column(name = "phone", nullable = false, length = 16)
    private String phone;

    @Column(name = "email", length = 160)
    private String email;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ReferenceStatus status = ReferenceStatus.PENDING;

    @Setter(AccessLevel.NONE)
    @Column(name = "notes", length = 1000)
    private String notes;

    @Setter(AccessLevel.NONE)
    @Column(name = "checked_by_id")
    private UUID checkedById;

    @Setter(AccessLevel.NONE)
    @Column(name = "checked_at")
    private Instant checkedAt;

    public CandidateReference(UUID candidateId) {
        this.candidateId = candidateId;
    }

    /** Records the result of a check; back to {@code pending} clears who checked it. */
    public void check(ReferenceStatus newStatus, String newNotes, UUID checkerId, Instant now) {
        status = newStatus;
        notes = newNotes;
        boolean checked = newStatus != ReferenceStatus.PENDING;
        checkedById = checked ? checkerId : null;
        checkedAt = checked ? now : null;
    }
}

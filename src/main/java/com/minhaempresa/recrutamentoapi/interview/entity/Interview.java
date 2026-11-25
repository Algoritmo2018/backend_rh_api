package com.minhaempresa.recrutamentoapi.interview.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
 * An interview of an application (frontend {@code Interview}). Times in UTC. {@code startsAt} is {@code null} while
 * waiting for the candidate (or the client, for a client interview) to choose one of the proposed slots.
 */
@Getter
@Setter
@Entity
@Table(name = "interviews")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Interview extends UserAuditableEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10, updatable = false)
    private InterviewType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 10)
    private InterviewMode mode;

    @Column(name = "location", length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_provider", length = 10)
    private MeetingProvider meetingProvider;

    @Column(name = "meeting_url", length = 500)
    private String meetingUrl;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private InterviewStatus status;

    /** Internal notes: never shown to the candidate nor the client; blanked on erasure. */
    @Column(name = "notes", length = 1000)
    private String notes;

    /** SEQUENCE of the calendar invitation (same UID on every change). */
    @Setter(AccessLevel.NONE)
    @Column(name = "ics_sequence", nullable = false)
    private int icsSequence;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "slots_expired_notified_at")
    private Instant slotsExpiredNotifiedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "interview_proposed_slots", joinColumns = @JoinColumn(name = "interview_id"))
    @OrderColumn(name = "position")
    @Column(name = "starts_at", nullable = false)
    private List<Instant> proposedSlots = new ArrayList<>();

    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "interview_interviewers", joinColumns = @JoinColumn(name = "interview_id"))
    @Column(name = "user_id", nullable = false)
    private Set<UUID> interviewerIds = new LinkedHashSet<>();

    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "interview_client_participants", joinColumns = @JoinColumn(name = "interview_id"))
    @Column(name = "user_id", nullable = false)
    private Set<UUID> clientParticipantIds = new LinkedHashSet<>();

    public Interview(UUID applicationId, UUID jobId, UUID candidateId, InterviewType type) {
        this.applicationId = applicationId;
        this.jobId = jobId;
        this.candidateId = candidateId;
        this.type = type;
    }

    /** End of the interview, or {@code null} without a time. */
    public Instant endsAt() {
        return startsAt == null ? null : startsAt.plusSeconds(durationMinutes * 60L);
    }

    /** Fixed time: scheduled, the proposed slots are dropped. */
    public void scheduleAt(Instant time) {
        startsAt = time;
        status = InterviewStatus.SCHEDULED;
        proposedSlots.clear();
        reminderSentAt = null;
    }

    /** Waiting for a choice between these slots. */
    public void proposeSlots(List<Instant> slots) {
        startsAt = null;
        status = InterviewStatus.PENDING_SLOT;
        proposedSlots.clear();
        proposedSlots.addAll(slots);
        reminderSentAt = null;
        slotsExpiredNotifiedAt = null;
    }

    /** A change the calendars must see (time, place, cancellation): new SEQUENCE of the same UID. */
    public void bumpCalendarSequence() {
        icsSequence++;
    }

    public void replaceInterviewers(Set<UUID> ids) {
        interviewerIds.retainAll(ids);
        interviewerIds.addAll(ids);
    }

    public void replaceClientParticipants(Set<UUID> ids) {
        clientParticipantIds.retainAll(ids);
        clientParticipantIds.addAll(ids);
    }

    @Override
    public String toString() {
        return "Interview(id=" + getId() + ", status=" + status + ")";
    }
}

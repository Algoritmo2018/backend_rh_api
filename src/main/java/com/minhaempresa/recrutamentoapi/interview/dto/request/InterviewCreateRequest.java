package com.minhaempresa.recrutamentoapi.interview.dto.request;

import com.minhaempresa.recrutamentoapi.interview.entity.InterviewMode;
import com.minhaempresa.recrutamentoapi.interview.entity.InterviewType;
import com.minhaempresa.recrutamentoapi.interview.entity.MeetingProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /interviews} (frontend {@code InterviewInput}; {@code createdById}, {@code jobId}, {@code candidateId}
 * and {@code status} come from the server). {@code startsAt} (fixed time) or 2–5 {@code proposedSlots}.
 *
 * @param clientParticipantIds client interview only; empty = every active portal user of the job's company
 * @param notifyCandidate send the invitation (email with {@code .ics}, WhatsApp) or the "choose a time" email
 * @param ignoreConflicts schedule even if an interviewer or the candidate already has an overlapping interview
 */
public record InterviewCreateRequest(
        @NotNull(message = "{validation.required}") UUID applicationId,
        @NotNull(message = "{validation.required}") InterviewType type,
        @NotNull(message = "{validation.required}") InterviewMode mode,
        @Size(max = 200, message = "{validation.interview.location}") String location,
        MeetingProvider meetingProvider,
        @Size(max = 500, message = "{validation.interview.meeting-url}") String meetingUrl,
        Instant startsAt,
        @Size(max = 5, message = "{validation.interview.slots}") List<@NotNull(message = "{validation.required}") Instant> proposedSlots,
        @NotNull(message = "{validation.required}") @Min(value = 15, message = "{validation.interview.duration}")
        @Max(value = 240, message = "{validation.interview.duration}") Integer durationMinutes,
        @NotEmpty(message = "{validation.interview.interviewers}") @Size(max = 10, message = "{validation.interview.interviewers}")
        List<@NotNull(message = "{validation.required}") UUID> interviewerIds,
        @Size(max = 50, message = "{validation.invalid-value}") List<@NotNull(message = "{validation.required}") UUID> clientParticipantIds,
        @Size(max = 1000, message = "{validation.interview.notes}") String notes,
        Boolean notifyCandidate,
        Boolean ignoreConflicts) {}

package com.minhaempresa.recrutamentoapi.interview.dto.request;

import com.minhaempresa.recrutamentoapi.interview.entity.InterviewMode;
import com.minhaempresa.recrutamentoapi.interview.entity.MeetingProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code PATCH /interviews/{id}}: reschedule or edit an interview still to happen. Absent fields stay. A new
 * {@code startsAt} fixes the time (also "Fixar" a proposed slot); new {@code proposedSlots} without {@code startsAt}
 * go back to waiting for a choice. The type and the application never change; a {@code status} sent by the frontend
 * is ignored (see {@code PATCH /interviews/{id}/status}).
 *
 * @param version the version read with the interview: someone else changed it meanwhile → 409
 */
public record InterviewUpdateRequest(
        InterviewMode mode,
        @Size(max = 200, message = "{validation.interview.location}") String location,
        MeetingProvider meetingProvider,
        @Size(max = 500, message = "{validation.interview.meeting-url}") String meetingUrl,
        Instant startsAt,
        @Size(max = 5, message = "{validation.interview.slots}") List<@NotNull(message = "{validation.required}") Instant> proposedSlots,
        @Min(value = 15, message = "{validation.interview.duration}") @Max(value = 240, message = "{validation.interview.duration}") Integer durationMinutes,
        @Size(min = 1, max = 10, message = "{validation.interview.interviewers}") List<@NotNull(message = "{validation.required}") UUID> interviewerIds,
        @Size(max = 50, message = "{validation.invalid-value}") List<@NotNull(message = "{validation.required}") UUID> clientParticipantIds,
        @Size(max = 1000, message = "{validation.interview.notes}") String notes,
        Boolean notifyCandidate,
        Boolean ignoreConflicts,
        @NotNull(message = "{validation.required}") Long version) {}

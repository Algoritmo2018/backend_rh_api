package com.minhaempresa.recrutamentoapi.interview.event;

import com.minhaempresa.recrutamentoapi.interview.entity.InterviewMode;
import com.minhaempresa.recrutamentoapi.interview.entity.InterviewType;
import com.minhaempresa.recrutamentoapi.interview.entity.MeetingProvider;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Copy of an interview for its notices (taken when it changed).
 *
 * @param icsSequence SEQUENCE of the calendar invitation (UID fixed per interview)
 * @param candidateName for staff notices only (never sent to the client)
 */
public record InterviewNotice(
        UUID interviewId,
        UUID applicationId,
        UUID candidateId,
        UUID jobId,
        String jobTitle,
        UUID clientId,
        UUID recruiterId,
        InterviewType type,
        InterviewMode mode,
        String location,
        MeetingProvider meetingProvider,
        String meetingUrl,
        Instant startsAt,
        int durationMinutes,
        int icsSequence,
        Set<UUID> interviewerIds,
        Set<UUID> clientParticipantIds,
        String candidateName) {

    @Override
    public String toString() {
        return "InterviewNotice(interviewId=" + interviewId + ")";
    }
}

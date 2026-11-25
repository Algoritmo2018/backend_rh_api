package com.minhaempresa.recrutamentoapi.interview.dto.response;

import com.minhaempresa.recrutamentoapi.interview.entity.InterviewMode;
import com.minhaempresa.recrutamentoapi.interview.entity.InterviewStatus;
import com.minhaempresa.recrutamentoapi.interview.entity.InterviewType;
import com.minhaempresa.recrutamentoapi.interview.entity.MeetingProvider;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Frontend {@code InterviewWithRefs}: the interview plus the names the agenda shows, and {@code version}.
 *
 * @param startsAt UTC; {@code null} while waiting for a slot
 * @param candidateHasWhatsapp to choose WhatsApp or SMS
 * @param version send it back when editing or changing the status (stale → 409)
 */
public record InterviewResponse(
        UUID id,
        UUID applicationId,
        UUID jobId,
        UUID candidateId,
        InterviewType type,
        InterviewMode mode,
        String location,
        MeetingProvider meetingProvider,
        String meetingUrl,
        Instant startsAt,
        int durationMinutes,
        List<Instant> proposedSlots,
        Set<UUID> interviewerIds,
        Set<UUID> clientParticipantIds,
        InterviewStatus status,
        String notes,
        UUID createdById,
        Instant createdAt,
        Instant updatedAt,
        long version,
        String candidateName,
        boolean candidateHasWhatsapp,
        String jobTitle,
        UUID clientId,
        String clientName,
        List<String> interviewerNames) {}

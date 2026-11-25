package com.minhaempresa.recrutamentoapi.interview.event;

import java.util.Set;
import java.util.UUID;

/**
 * An interview was scheduled, changed or cancelled: the candidate, the interviewers and the client participants are
 * told in the same transaction (bells, outbox emails with the {@code .ics}, messages to the candidate).
 *
 * @param actorId the user who acted (not told); {@code null} = the candidate, the client or the system
 * @param notifyCandidate send the candidate the invitation / "choose a time" / cancellation message
 * @param scheduleChanged time, place, link or duration changed (the interviewers who stay are told too)
 * @param addedInterviewers interviewers (and client participants) added by an edit: told as for a new interview
 * @param removedUsers interviewers and client participants removed by an edit: told as for a cancellation
 * @param anonymous erasure of the candidate: no message to the candidate and no name in the notices
 */
public record InterviewChangedEvent(
        InterviewNotice interview,
        Change change,
        UUID actorId,
        boolean notifyCandidate,
        boolean scheduleChanged,
        Set<UUID> addedInterviewers,
        Set<UUID> removedUsers,
        boolean anonymous) {

    public enum Change {
        /** Created with a fixed time. */
        SCHEDULED,
        /** Created (or edited) with proposed slots: waiting for a choice. */
        SLOTS_PROPOSED,
        /** Time, place, link, duration or people changed. */
        RESCHEDULED,
        /** One of the proposed slots was chosen (or fixed by the staff). */
        SLOT_CHOSEN,
        CANCELLED
    }
}

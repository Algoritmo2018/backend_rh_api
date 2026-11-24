package com.minhaempresa.recrutamentoapi.client.event;

import com.minhaempresa.recrutamentoapi.client.entity.JobRequestStatus;
import java.util.UUID;

/**
 * A job request was rejected or converted into a job: the client user who made it is told.
 *
 * @param requestedById the requesting user ({@code null} if that user no longer exists)
 * @param note reason of a rejection
 */
public record JobRequestReviewedEvent(UUID jobRequestId, UUID requestedById, String title, JobRequestStatus status, String note) {}

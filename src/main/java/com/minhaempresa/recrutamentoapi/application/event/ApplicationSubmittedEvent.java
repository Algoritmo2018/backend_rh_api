package com.minhaempresa.recrutamentoapi.application.event;

import java.util.UUID;

/**
 * A new application was stored (public form, logged-in candidate). After the commit the automatic action of the
 * "Nova" stage runs; in the transaction the job's recruiter is told in the bell (D8-4).
 *
 * @param recruiterId the job's responsible recruiter
 */
public record ApplicationSubmittedEvent(UUID applicationId, UUID jobId, String jobTitle, UUID recruiterId, UUID candidateId, String candidateName) {

    @Override
    public String toString() {
        return "ApplicationSubmittedEvent(applicationId=" + applicationId + ")";
    }
}

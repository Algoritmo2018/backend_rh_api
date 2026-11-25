package com.minhaempresa.recrutamentoapi.messaging.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Implemented by the applications module (stage 8) so a message can be about an application without the
 * messaging module depending on it: the application must belong to the candidate, gives the consent to
 * contact them (job application) and the values of the job variables.
 */
public interface MessageApplicationDirectory {

    Optional<ApplicationRef> find(UUID applicationId);

    /**
     * @param companyName the client name, or the neutral text of a confidential job (never the real name)
     */
    record ApplicationRef(UUID id, UUID candidateId, String jobTitle, String companyName) {}
}

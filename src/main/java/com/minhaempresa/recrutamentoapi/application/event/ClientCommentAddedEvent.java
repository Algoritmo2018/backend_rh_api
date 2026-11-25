package com.minhaempresa.recrutamentoapi.application.event;

import java.util.UUID;

/**
 * FEH OIRAM wrote in the shortlist of an application (stage 8c): the client company's portal users get a bell and an
 * email, purpose {@code clients}, without the candidate's name.
 */
public record ClientCommentAddedEvent(UUID jobId, String jobTitle, UUID clientId) {}

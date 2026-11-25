package com.minhaempresa.recrutamentoapi.application.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** Frontend {@code PipelineCard}: the application's fields plus {@code candidate}. */
public record PipelineCardResponse(@JsonUnwrapped ApplicationResponse application, PipelineCandidateResponse candidate) {}

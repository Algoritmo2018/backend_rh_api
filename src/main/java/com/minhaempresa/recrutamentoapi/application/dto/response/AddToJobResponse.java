package com.minhaempresa.recrutamentoapi.application.dto.response;

/**
 * @param added new applications (stage "Triagem")
 * @param duplicates candidates who already had an application to the job (left as they were)
 */
public record AddToJobResponse(int added, int duplicates) {}

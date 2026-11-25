package com.minhaempresa.recrutamentoapi.application.dto.response;

/** Applications moved (those rejected, withdrawn or already in the stage are skipped). */
public record BulkMoveResponse(int moved) {}

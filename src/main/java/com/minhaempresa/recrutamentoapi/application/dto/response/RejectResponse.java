package com.minhaempresa.recrutamentoapi.application.dto.response;

/** Applications rejected (those not in progress are skipped). */
public record RejectResponse(int rejected) {}

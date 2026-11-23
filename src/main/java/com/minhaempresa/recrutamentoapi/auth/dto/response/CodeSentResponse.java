package com.minhaempresa.recrutamentoapi.auth.dto.response;

/**
 * An SMS code was (or would have been) sent; same shape as the frontend {@code requestOtp} result.
 * The answer is identical whether the phone has an account or not.
 */
public record CodeSentResponse(long expiresInSeconds) {}

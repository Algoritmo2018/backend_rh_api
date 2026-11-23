package com.minhaempresa.recrutamentoapi.shared.exception;

/**
 * Item of {@code errors[]} in an error response.
 *
 * @param field field path as in the request ({@code salary.amount}, {@code requirements[0].value})
 * @param message user-facing message (Portuguese)
 */
public record FieldErrorResponse(String field, String message) {}

package com.minhaempresa.recrutamentoapi.application.dto.response;

/** Author of a shortlist comment (frontend {@code commentAuthors} value). */
public record CommentAuthorResponse(String name, boolean fromClient) {}

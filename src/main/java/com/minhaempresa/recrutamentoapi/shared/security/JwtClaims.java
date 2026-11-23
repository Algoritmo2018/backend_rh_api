package com.minhaempresa.recrutamentoapi.shared.security;

/** Names of the custom claims in the access token ({@code sub} holds the user id). */
public final class JwtClaims {

    /** Roles as Java constant names, e.g. {@code ["RECRUITER"]} → authority {@code ROLE_RECRUITER}. */
    public static final String ROLES = "roles";
    /** Session id: the refresh-token family the access token was issued for. */
    public static final String SESSION_ID = "sid";
    /** Client company of a {@code CLIENT} user. */
    public static final String CLIENT_ID = "clientId";
    /** Candidate profile of a {@code CANDIDATE} user. */
    public static final String CANDIDATE_ID = "candidateId";

    private JwtClaims() {}
}

package com.minhaempresa.recrutamentoapi.user.service;

import java.util.UUID;

/**
 * Lets other modules that keep a copy of an account's email take part in a candidate's email change without
 * the users module depending on them (e.g. {@code candidates.email}).
 */
public interface AccountEmailContributor {

    /** True if {@code email} (lower case) is used by a record of this module that does not belong to {@code userId}. */
    boolean isEmailUsedByOthers(String email, UUID userId);

    /**
     * Called in the transaction of a confirmed change, after {@code users.email} changed. The caller flushes:
     * a unique violation here makes the whole change fail.
     */
    void onAccountEmailChanged(UUID userId, String newEmail);
}

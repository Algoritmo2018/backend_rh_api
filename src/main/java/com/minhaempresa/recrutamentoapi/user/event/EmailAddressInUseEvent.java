package com.minhaempresa.recrutamentoapi.user.event;

/**
 * An email change asked for an address that already belongs to an account or to a candidate without an
 * account: that address is told so and nothing changes. The requester gets the same answer as for a free
 * address (no account enumeration) and profiles are never merged.
 */
public record EmailAddressInUseEvent(String email) {}

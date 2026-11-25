package com.minhaempresa.recrutamentoapi.user.event;

import java.time.Instant;

/**
 * Tell the CURRENT address of an account that someone asked to change it (nothing changes until the link
 * sent to the new address is followed). Published for every accepted request, whether the new address is
 * free or not, so the requester learns nothing about other accounts.
 */
public record EmailChangeNoticeEvent(String email, String name, Instant requestedAt) {}

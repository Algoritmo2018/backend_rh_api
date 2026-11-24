package com.minhaempresa.recrutamentoapi.user.listener;

import com.minhaempresa.recrutamentoapi.client.event.ClientDeactivatedEvent;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ends the sessions of the portal users of a deactivated client, in the same transaction as the
 * deactivation (both happen or neither does). Their users stay active individually: reactivating the
 * client lets them log in again.
 */
@Component
@RequiredArgsConstructor
public class ClientDeactivatedListener {

    private final UserService userService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onClientDeactivated(ClientDeactivatedEvent event) {
        userService.revokeSessionsOfClient(event.clientId());
    }
}

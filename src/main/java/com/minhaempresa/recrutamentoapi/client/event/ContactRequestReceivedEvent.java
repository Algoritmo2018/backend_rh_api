package com.minhaempresa.recrutamentoapi.client.event;

import com.minhaempresa.recrutamentoapi.client.entity.ContactRequestKind;
import java.util.UUID;

/**
 * Someone sent a public contact form: the administrators are told by email (in-app notifications in
 * stage 7). Carries personal data, so {@link #toString()} leaves it out.
 */
public record ContactRequestReceivedEvent(
        UUID contactRequestId,
        ContactRequestKind kind,
        String name,
        String company,
        String email,
        String phone,
        String message,
        Integer vacancies) {

    @Override
    public String toString() {
        return "ContactRequestReceivedEvent(id=" + contactRequestId + ", kind=" + kind + ")";
    }
}

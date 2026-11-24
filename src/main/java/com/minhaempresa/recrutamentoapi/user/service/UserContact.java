package com.minhaempresa.recrutamentoapi.user.service;

import java.util.UUID;

/** Who to send a notification to. Internal to the backend (never returned by a controller). */
public record UserContact(UUID id, String name, String email) {

    @Override
    public String toString() {
        return "UserContact(id=" + id + ")";
    }
}

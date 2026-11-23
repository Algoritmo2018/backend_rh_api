package com.minhaempresa.recrutamentoapi.user.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * First administrator ({@code app.bootstrap.admin.*}). When set and there is no administrator with a
 * password, it is created on startup without a password and receives an invitation email. No password
 * is ever configured.
 */
@ConfigurationProperties(prefix = "app.bootstrap.admin")
public record BootstrapAdminProperties(String email, String name, String phone) {

    public boolean isConfigured() {
        return email != null && !email.isBlank();
    }
}

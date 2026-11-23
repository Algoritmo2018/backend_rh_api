package com.minhaempresa.recrutamentoapi.shared.web;

import com.minhaempresa.recrutamentoapi.shared.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The only place that builds links to frontend pages (emails, notifications). The routes are the
 * frontend's (Portuguese paths) and must stay in sync with {@code Front_RH/src/app/router.tsx}.
 */
@Component
@RequiredArgsConstructor
public class FrontendLinks {

    private final AppProperties properties;

    public String login() {
        return builder("/login").toUriString();
    }

    /** Existing frontend page where the user asks for a password-reset link. */
    public String forgotPassword() {
        return builder("/recuperar-senha").toUriString();
    }

    /** Pending frontend route (integration stage). */
    public String passwordReset(String token) {
        return withToken("/redefinir-senha", token);
    }

    /** Pending frontend route (integration stage). */
    public String invitation(String token) {
        return withToken("/aceitar-convite", token);
    }

    /** Pending frontend route (integration stage). */
    public String emailVerification(String token) {
        return withToken("/confirmar-email", token);
    }

    /** The token is a URI variable, so it is strictly encoded (any reserved character is escaped). */
    private String withToken(String path, String token) {
        return builder(path).queryParam("token", "{token}").encode().buildAndExpand(token).toUriString();
    }

    private UriComponentsBuilder builder(String path) {
        return UriComponentsBuilder.fromUriString(properties.frontendUrl()).path(path);
    }
}

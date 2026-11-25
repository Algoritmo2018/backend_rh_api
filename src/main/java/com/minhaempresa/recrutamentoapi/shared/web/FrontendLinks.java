package com.minhaempresa.recrutamentoapi.shared.web;

import com.minhaempresa.recrutamentoapi.shared.config.AppProperties;
import java.util.UUID;
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

    /** Pending frontend route (integration stage): confirms a candidate's new email. */
    public String emailChange(String token) {
        return withToken("/confirmar-novo-email", token);
    }

    /** Public contact page. */
    public String contact() {
        return builder("/contacto").toUriString();
    }

    /** Client list of the internal panel. */
    public String clients() {
        return builder("/app/clientes").toUriString();
    }

    /** Client detail, tab "Pedidos" (job requests). */
    public String clientJobRequests(UUID clientId) {
        return builder("/app/clientes/{id}").queryParam("separador", "pedidos").buildAndExpand(clientId).toUriString();
    }

    /** Client portal page with the form and the list of job requests. */
    public String clientPortalJobRequests() {
        return builder("/cliente/pedir-vaga").toUriString();
    }

    /** Pipeline (kanban) of a job in the internal panel (administrators and recruiters). */
    public String job(UUID jobId) {
        return builder("/app/vagas/{id}").buildAndExpand(jobId).toUriString();
    }

    /** Interview agenda of the internal panel (every staff role). */
    public String interviews() {
        return builder("/app/entrevistas").toUriString();
    }

    /** Messages page of the internal panel (administrators and recruiters). */
    public String messages() {
        return builder("/app/mensagens").toUriString();
    }

    /** Settings of the internal panel (administrators). */
    public String settings() {
        return builder("/app/configuracoes").toUriString();
    }

    /**
     * The same link as a path inside the frontend app ({@code /app/vagas/…}), for in-app notifications
     * (the frontend router navigates to it; emails use the full URL).
     */
    public String inAppPath(String link) {
        String base = properties.frontendUrl().replaceAll("/+$", "");
        return link != null && link.startsWith(base) ? link.substring(base.length()) : link;
    }

    /** The token is a URI variable, so it is strictly encoded (any reserved character is escaped). */
    private String withToken(String path, String token) {
        return builder(path).queryParam("token", "{token}").encode().buildAndExpand(token).toUriString();
    }

    private UriComponentsBuilder builder(String path) {
        return UriComponentsBuilder.fromUriString(properties.frontendUrl()).path(path);
    }
}

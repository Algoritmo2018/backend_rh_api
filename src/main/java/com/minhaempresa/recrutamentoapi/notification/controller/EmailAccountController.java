package com.minhaempresa.recrutamentoapi.notification.controller;

import com.minhaempresa.recrutamentoapi.notification.dto.request.EmailAccountRequest;
import com.minhaempresa.recrutamentoapi.notification.dto.request.EmailAccountTestRequest;
import com.minhaempresa.recrutamentoapi.notification.dto.request.EmailRouteRequest;
import com.minhaempresa.recrutamentoapi.notification.dto.response.EmailAccountResponse;
import com.minhaempresa.recrutamentoapi.notification.dto.response.EmailRouteResponse;
import com.minhaempresa.recrutamentoapi.notification.service.EmailAccountService;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Settings tab "Emails de envio": SMTP accounts and which one sends each kind of email. Administrators only. */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EmailAccountController {

    private final EmailAccountService service;

    @GetMapping("/email-accounts")
    public List<EmailAccountResponse> list() {
        return service.list();
    }

    @PostMapping("/email-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public EmailAccountResponse create(@Valid @RequestBody EmailAccountRequest request) {
        return service.create(request);
    }

    /** Blank password = keep the stored one. */
    @PutMapping("/email-accounts/{id}")
    public EmailAccountResponse update(@PathVariable UUID id, @Valid @RequestBody EmailAccountRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/email-accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    /** Sends a test email to the logged-in administrator with the data of the form (before saving). */
    @PostMapping("/email-accounts/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void test(@Valid @RequestBody EmailAccountTestRequest request, AuthenticatedUser actor) {
        service.sendTest(request, actor);
    }

    @GetMapping("/email-routes")
    public List<EmailRouteResponse> routes() {
        return service.listRoutes();
    }

    @PutMapping("/email-routes")
    public List<EmailRouteResponse> updateRoutes(
            @RequestBody @Size(max = 3, message = "{validation.invalid-value}") List<@Valid @NotNull(message = "{validation.required}") EmailRouteRequest> request) {
        return service.updateRoutes(request);
    }
}

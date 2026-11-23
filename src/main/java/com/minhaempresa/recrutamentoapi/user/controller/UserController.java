package com.minhaempresa.recrutamentoapi.user.controller;

import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.user.dto.request.UserCreateRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.UserFilterRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.UserStatusRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.UserUpdateRequest;
import com.minhaempresa.recrutamentoapi.user.dto.response.UserResponse;
import com.minhaempresa.recrutamentoapi.user.dto.response.UserSummaryResponse;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** User administration (Configurações → Utilizadores e papéis). Administrators only, except the staff picker. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> list(@Valid UserFilterRequest filter) {
        return userService.list(filter);
    }

    /** Internal staff for pickers (job form, interviews, agenda). */
    @GetMapping("/internal")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'INTERVIEWER')")
    public List<UserSummaryResponse> internal() {
        return userService.listInternal();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request, AuthenticatedUser actor) {
        return userService.update(id, request, actor);
    }

    @PostMapping("/{id}/invitation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void resendInvitation(@PathVariable UUID id) {
        userService.resendInvitation(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse setStatus(@PathVariable UUID id, @Valid @RequestBody UserStatusRequest request, AuthenticatedUser actor) {
        return userService.setStatus(id, request, actor);
    }
}

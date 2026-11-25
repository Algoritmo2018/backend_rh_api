package com.minhaempresa.recrutamentoapi.user.controller;

import com.minhaempresa.recrutamentoapi.auth.dto.response.CodeSentResponse;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.user.dto.request.ChangePasswordRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.ChangePhoneRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.EmailChangeRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.PhoneCodeRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.TwoFactorRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.UpdateMyProfileRequest;
import com.minhaempresa.recrutamentoapi.user.dto.response.UserResponse;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in user's own account, any role. */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;

    @PatchMapping
    public UserResponse updateProfile(AuthenticatedUser actor, @Valid @RequestBody UpdateMyProfileRequest request) {
        return userService.updateMyProfile(actor, request);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(AuthenticatedUser actor, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changeMyPassword(actor, request);
    }

    /** Code to the new number; needed only when two-step verification is on. */
    @PostMapping("/phone/code")
    public CodeSentResponse requestPhoneChangeCode(AuthenticatedUser actor, @Valid @RequestBody PhoneCodeRequest request) {
        return userService.requestPhoneChangeCode(actor, request);
    }

    @PutMapping("/phone")
    public UserResponse changePhone(AuthenticatedUser actor, @Valid @RequestBody ChangePhoneRequest request) {
        return userService.changeMyPhone(actor, request);
    }

    /**
     * Candidates only (staff emails never change). Always 204 once the password is right: the link goes
     * to the new address, confirmed by {@code POST /auth/email/change/confirm}.
     */
    @PostMapping("/email/change")
    @PreAuthorize("hasRole('CANDIDATE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestEmailChange(AuthenticatedUser actor, @Valid @RequestBody EmailChangeRequest request) {
        userService.requestEmailChange(actor, request);
    }

    @PostMapping("/two-factor/code")
    public CodeSentResponse requestTwoFactorCode(AuthenticatedUser actor) {
        return userService.requestTwoFactorCode(actor);
    }

    @PutMapping("/two-factor")
    public UserResponse setTwoFactor(AuthenticatedUser actor, @Valid @RequestBody TwoFactorRequest request) {
        return userService.setMyTwoFactor(actor, request);
    }
}

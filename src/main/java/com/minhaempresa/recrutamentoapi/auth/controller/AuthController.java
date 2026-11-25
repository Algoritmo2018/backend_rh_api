package com.minhaempresa.recrutamentoapi.auth.controller;

import com.minhaempresa.recrutamentoapi.auth.dto.request.AcceptInvitationRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.ConfirmEmailChangeRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.ForgotPasswordRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.LoginRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.OtpRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.OtpVerifyRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.RegisterRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.ResetPasswordRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.TwoFactorLoginRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.VerifyEmailRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.response.CodeSentResponse;
import com.minhaempresa.recrutamentoapi.auth.dto.response.LoginResponse;
import com.minhaempresa.recrutamentoapi.auth.dto.response.SessionResponse;
import com.minhaempresa.recrutamentoapi.auth.service.AuthService;
import com.minhaempresa.recrutamentoapi.auth.service.AuthService.AuthResult;
import com.minhaempresa.recrutamentoapi.auth.service.AuthService.ClientInfo;
import com.minhaempresa.recrutamentoapi.auth.service.AuthService.LoginResult;
import com.minhaempresa.recrutamentoapi.shared.exception.UnauthorizedException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.user.dto.response.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication. Every endpoint is public except {@code GET /me} and {@code POST /email/verify/resend}.
 * Endpoints that start a session return the access token in the body and set the refresh token cookie.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookies cookies;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        LoginResult result = authService.login(request, clientInfo(http));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.refreshToken() != null) {
            response.header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()).toString());
        }
        return response.body(result.response());
    }

    @PostMapping("/login/two-factor")
    public ResponseEntity<SessionResponse> loginWithTwoFactor(@Valid @RequestBody TwoFactorLoginRequest request, HttpServletRequest http) {
        return session(HttpStatus.OK, authService.loginWithTwoFactor(request, clientInfo(http)));
    }

    @PostMapping("/otp/request")
    public CodeSentResponse requestOtp(@Valid @RequestBody OtpRequest request, HttpServletRequest http) {
        return authService.requestOtp(request, clientInfo(http));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<SessionResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request, HttpServletRequest http) {
        return session(HttpStatus.OK, authService.verifyOtp(request, clientInfo(http)));
    }

    @PostMapping("/register")
    public ResponseEntity<SessionResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return session(HttpStatus.CREATED, authService.register(request, clientInfo(http)));
    }

    /** Uses the refresh-token cookie; the frontend must call it with {@code credentials: 'include'}. */
    @PostMapping("/refresh")
    public ResponseEntity<SessionResponse> refresh(HttpServletRequest http) {
        String token = cookies.read(http);
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("auth.session.expired");
        }
        return session(HttpStatus.OK, authService.refresh(token, clientInfo(http)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        String token = cookies.read(http);
        if (token != null && !token.isBlank()) {
            authService.logout(token);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        authService.forgotPassword(request, clientInfo(http));
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<SessionResponse> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request, HttpServletRequest http) {
        return session(HttpStatus.OK, authService.acceptInvitation(request, clientInfo(http)));
    }

    @PostMapping("/email/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
    }

    /**
     * Public (the link may be opened logged out). When the browser sends the refresh cookie of the same
     * account ({@code credentials: 'include'}), that session is kept; every other one ends.
     */
    @PostMapping("/email/change/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmailChange(@Valid @RequestBody ConfirmEmailChangeRequest request, HttpServletRequest http) {
        authService.confirmEmailChange(request, cookies.read(http));
    }

    @PostMapping("/email/verify/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendEmailVerification(AuthenticatedUser actor) {
        authService.resendEmailVerification(actor);
    }

    @GetMapping("/me")
    public UserResponse me(AuthenticatedUser actor) {
        return authService.me(actor);
    }

    private ResponseEntity<SessionResponse> session(HttpStatus status, AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()).toString())
                .body(result.session());
    }

    private static ClientInfo clientInfo(HttpServletRequest http) {
        return new ClientInfo(http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }
}

package com.minhaempresa.recrutamentoapi.auth.dto.request;

import com.minhaempresa.recrutamentoapi.shared.validation.AngolanPhone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Log in with the SMS code. */
public record OtpVerifyRequest(
        @NotBlank(message = "{validation.required}") @AngolanPhone String phone,
        @NotBlank(message = "{validation.required}") @Pattern(regexp = "\\d{6}", message = "{validation.code.invalid}") String code) {}

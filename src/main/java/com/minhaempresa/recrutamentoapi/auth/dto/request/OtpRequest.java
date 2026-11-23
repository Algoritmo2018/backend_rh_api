package com.minhaempresa.recrutamentoapi.auth.dto.request;

import com.minhaempresa.recrutamentoapi.shared.validation.AngolanPhone;
import jakarta.validation.constraints.NotBlank;

/** Ask for an SMS login code. */
public record OtpRequest(@NotBlank(message = "{validation.required}") @AngolanPhone String phone) {}
